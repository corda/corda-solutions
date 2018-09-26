package net.corda.cordaupdates.transport.flows

import com.sun.xml.internal.messaging.saaj.util.ByteInputStream
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.flows.FlowException
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.AppServiceHub
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.spi.connector.transport.AbstractTransporter
import org.eclipse.aether.spi.connector.transport.GetTask
import org.eclipse.aether.spi.connector.transport.PeekTask
import org.eclipse.aether.spi.connector.transport.PutTask
import org.eclipse.aether.spi.connector.transport.Transporter
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transfer.NoTransporterException
import org.eclipse.aether.util.ConfigUtils

class FlowsTransporterFactory : TransporterFactory {
    override fun getPriority() = 5.0f

    override fun newInstance(session : RepositorySystemSession?, repository : RemoteRepository?) : Transporter {
        return FlowsTransporter(session!!, repository!!)
    }
}

class RPCTransporterFactory : TransporterFactory {
    override fun getPriority() = 5.0f

    override fun newInstance(session : RepositorySystemSession?, repository : RemoteRepository?) : Transporter {
        return RPCTransporter(session!!, repository!!)
    }
}

class FlowsTransporter(private val session : RepositorySystemSession,
                       private val repository : RemoteRepository) : AbstractTransporter() {

    private val bnoName = repository.url.substring(repository.protocol!!.length + 1, repository.url.length)

    init {
        session.configProperties
        if (!"flow".equals(repository.protocol, ignoreCase = true)) {
            throw NoTransporterException(repository)
        }
    }

    override fun implPeek(task : PeekTask?) {
        val appServiceHub = ConfigUtils.getObject(session, null, ConfigurationProperties.APP_SERVICE_HUB) as AppServiceHub
        appServiceHub.startFlow(PeekArtifactFlow(task!!.location.toString(), bnoName)).returnValue.getOrThrow()
    }

    override fun implGet(task : GetTask?) {
        val appServiceHub = ConfigUtils.getObject(session, null, ConfigurationProperties.APP_SERVICE_HUB) as AppServiceHub
        val bytes = appServiceHub.startFlow(GetResourceFlow(task!!.location.toString(), bnoName)).returnValue.getOrThrow()
        utilGet(task, ByteInputStream(bytes, bytes.size), true, bytes.size.toLong(), false)
    }

    override fun implPut(task : PutTask?) = throw Exception("Flows transport doesn't support PUT")

    override fun classify(error : Throwable?) : Int {
        if (error is ResourceNotFoundException)
            return Transporter.ERROR_NOT_FOUND
        return Transporter.ERROR_OTHER
    }

    override fun implClose() {
    }
}

class RPCTransporter(private val session : RepositorySystemSession,
                     private val repository : RemoteRepository) : AbstractTransporter() {

    private val bnoName = repository.url.substring(repository.protocol!!.length + 1, repository.url.length)

    init {
        session.configProperties
        if (!"rpc".equals(repository.protocol, ignoreCase = true)) {
            throw NoTransporterException(repository)
        }
    }

    override fun implPeek(task : PeekTask?) {
        rpcOps().startFlowDynamic(PeekArtifactFlow::class.java, task!!.location.toString(), bnoName).returnValue.getOrThrow()
    }

    override fun implGet(task : GetTask?) {
        val bytes = rpcOps().startFlowDynamic(GetResourceFlow::class.java, task!!.location.toString(), bnoName).returnValue.getOrThrow()
        utilGet(task, ByteInputStream(bytes, bytes.size), true, bytes.size.toLong(), false)
    }

    override fun implPut(task : PutTask?) = throw Exception("Flows transport doesn't support PUT")

    override fun classify(error : Throwable?) : Int {
        if (error is ResourceNotFoundException)
            return Transporter.ERROR_NOT_FOUND
        return Transporter.ERROR_OTHER
    }

    override fun implClose() {
    }

    private fun rpcOps() : CordaRPCOps {
        val host = ConfigUtils.getString(session, null, ConfigurationProperties.RPC_HOST)!!
        val port = ConfigUtils.getInteger(session, 0, ConfigurationProperties.RPC_PORT)
        val username = ConfigUtils.getString(session, null, ConfigurationProperties.RPC_USERNAME)!!
        val password = ConfigUtils.getString(session.configProperties, null, ConfigurationProperties.RPC_PASSWORD)

        val rpcAddress = NetworkHostAndPort(host, port)
        val rpcClient = CordaRPCClient(rpcAddress)
        val rpcConnection = rpcClient.start(username, password)
        return rpcConnection.proxy
    }
}

@CordaSerializable
class ResourceNotFoundException : FlowException()