package net.corda.cordaupdates.transport.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.cordaupdates.transport.flows.Utils.toCordaException
import net.corda.cordaupdates.transport.flows.Utils.transporterFactory
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.unwrap
import org.apache.maven.repository.internal.MavenRepositorySystemUtils.newSession
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.spi.connector.transport.GetTask
import org.eclipse.aether.spi.connector.transport.Transporter
import java.net.URI
import java.util.*

@StartableByService
@StartableByRPC
@InitiatingFlow
class GetResourceFlow(private val resourceLocation : String, private val bnoName : String) : FlowLogic<ByteArray>() {
    @Suspendable
    override fun call() : ByteArray {
        logger.info("Hello from here")
        val bnoParty = serviceHub.identityService.wellKnownPartyFromX500Name(CordaX500Name.parse(bnoName))!!
        val bnoSession = initiateFlow(bnoParty)

        val payloadLength = bnoSession.sendAndReceive<Int>(resourceLocation).unwrap { it }

        val byteArray = ByteArray(payloadLength)
        var position = 0
        while (position < payloadLength) {
            val receivedBytes = bnoSession.receive<ByteArray>().unwrap { it }
            System.arraycopy(receivedBytes, 0, byteArray, position, receivedBytes.size)
            position += receivedBytes.size
        }
        return byteArray
    }
}

@InitiatedBy(GetResourceFlow::class)
class GetResourceFlowResponder(private val session : FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val location = session.receive<String>().unwrap { it }
        val bytes = getArtifactBytes(location)

        val maxMessageSizeBytes = serviceHub.networkParameters.maxMessageSize

        session.send(bytes.size)
        for (i in 0..bytes.size step maxMessageSizeBytes) {
            val chunk = Arrays.copyOfRange(bytes, i, Math.min(i + maxMessageSizeBytes, bytes.size ))
            session.send(chunk)
        }
    }

    private fun getArtifactBytes(location : String) : ByteArray {
        val getTask = GetTask(URI.create(location))
        val transporter = createTransporter()
        try {
            transporter.get(getTask)
        } catch (ex : Exception) {
            logger.info("Error resolving resource at $location", ex)
            throw toCordaException(ex, transporter)
        }
        return getTask.dataBytes
    }

    private fun createTransporter() : Transporter {
        val configuration = serviceHub.cordaService(BNOConfigurationService::class.java)
        val repository = RemoteRepository.Builder("remote", "default", configuration.remoteRepoUrl()).build()
        val transporterFactory = transporterFactory(repository.protocol)
        val repositorySession = newSession()
        return transporterFactory.newInstance(repositorySession, repository)!!
    }
}