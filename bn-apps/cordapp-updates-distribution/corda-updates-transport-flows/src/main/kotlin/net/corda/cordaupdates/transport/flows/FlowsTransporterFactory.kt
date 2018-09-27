package net.corda.cordaupdates.transport.flows

import com.sun.xml.internal.messaging.saaj.util.ByteInputStream
import net.corda.core.flows.FlowException
import net.corda.core.node.AppServiceHub
import net.corda.core.serialization.CordaSerializable
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

@CordaSerializable
class ResourceNotFoundException : FlowException()