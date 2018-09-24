package net.corda.businessnetworks.cordaupdates.core.flowstransport

import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCConnection
import net.corda.core.flows.FlowLogic
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.spi.connector.transport.AbstractTransporter
import org.eclipse.aether.spi.connector.transport.GetTask
import org.eclipse.aether.spi.connector.transport.PeekTask
import org.eclipse.aether.spi.connector.transport.PutTask
import org.eclipse.aether.spi.connector.transport.TransportTask
import org.eclipse.aether.spi.connector.transport.Transporter
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transfer.NoTransporterException

class FlowsTransporterFactory : TransporterFactory {
    override fun getPriority() = 5.0f

    override fun newInstance(session : RepositorySystemSession?, repository : RemoteRepository?) : Transporter {
        return FlowsTransporter(repository!!)
    }
}

class FlowsTransporter(val repository : RemoteRepository) : AbstractTransporter() {
    companion object {
        var PORT : Int = 10
        var HOST : String = ""
    }


    init {
        if (!"flow".equals(repository.protocol, ignoreCase = true)) {
            throw NoTransporterException(repository)
        }

    }

    override fun implPeek(task : PeekTask?) {
//        val flowLogic = FlowLogic.currentTopLevel!!
//        val peekResult = flowLogic.subFlow(PeekArtifactFlow(task!!))
//        if (!peekResult)
//            throw ResourceNotFoundException("Resource ${task.location} has not been found")
    }

    override fun implGet(task : GetTask?) {
        val rpc = CordaRPCClient(NetworkHostAndPort(HOST, PORT))

        val rpcConnection : CordaRPCConnection
        try {
            rpcConnection = rpc.start("test", "test")
        } catch (ex : java.lang.Exception) {
            throw ex
        }
        val rpcOps = rpcConnection.proxy
        val bnoName = repository.url.substring(repository.protocol!!.length + 1, repository.url.length)
        TaskHolder.tasks.put(task!!.hashCode(), task)

        val future = rpcOps.startFlowDynamic(GetResourceFlow::class.java, task.hashCode(), bnoName)
        try {
            future.returnValue.getOrThrow()
        } finally {
            TaskHolder.tasks.remove(task.hashCode())
        }

    }

    override fun implPut(task : PutTask?) = throw FlowsTransporterException("Flows transport doesn't support PUT")

    override fun classify(error : Throwable?) : Int {
        if (error is ResourceNotFoundException)
            return Transporter.ERROR_NOT_FOUND
        return Transporter.ERROR_OTHER
    }

    override fun implClose() {
    }
}

class FlowsTransporterException(message : String) : Exception(message)

class ResourceNotFoundException(message : String) : Exception(message)

fun TransportTask.artifactCoords() = location.rawPath
