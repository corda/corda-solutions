package net.corda.businessnetworks.cordaupdates.core.flowstransport

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.cordaupdates.core.flowstransport.TaskHolder.getTask
import net.corda.businessnetworks.cordaupdates.core.flowstransport.Utils.transporterFactory
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.CordaX500Name
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.unwrap
import org.apache.maven.repository.internal.MavenRepositorySystemUtils.newSession
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.spi.connector.transport.GetTask
import org.eclipse.aether.spi.connector.transport.TransportListener
import org.eclipse.aether.spi.connector.transport.TransportTask
import java.net.URI
import java.nio.ByteBuffer

@StartableByRPC
@InitiatingFlow
class GetResourceFlow(private val getTaskHash : Int, val bnoName : String) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        logger.info("GetResourceFlow getTaskHash=$getTaskHash, bnoName=$bnoName")

        val bnoParty = serviceHub.identityService.wellKnownPartyFromX500Name(CordaX500Name.parse(bnoName))!!
        val bnoSession = initiateFlow(bnoParty)

        bnoSession.send(getTask(getTaskHash).location.toString())

        loop@ while (true) {
            val payload = bnoSession.receive<Any>().unwrap { it }
            when (payload) {
                is TransportStartedEvent ->  {
                    logger.info("GetResourceFlow TransportStartedEvent")
                    getTask(getTaskHash).listener.transportStarted(payload.dataOffset, payload.dataLength)
                }
                is TransportProgressedEvent -> {
                    logger.info("GetResourceFlow TransportProgressedEvent")
                    getTask(getTaskHash).listener.transportProgressed(payload.data)
                }
                is TransportFinishedEvent -> {
                    logger.info("GetResourceFlow TransportFinishedEvent")
                    break@loop
                }
                else -> throw FlowException("Unexpected message $payload")
            }
        }
    }
}

@InitiatedBy(GetResourceFlow::class)
class GetResourceFlowResponder(private val session : FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val location = session.receive<String>().unwrap { it }

        logger.info("GetResourceFlowResponder getTask=$location")

        val transporterFactory = transporterFactory(serviceHub)

        logger.info("GetResourceFlowResponder transporterFactory=$transporterFactory")

        val configuration = serviceHub.cordaService(BNOConfigurationService::class.java)

        val repository = RemoteRepository.Builder("remote", "default", configuration.remoteRepoUrl()).build()
        val repositorySession = newSession()

        val transporter = transporterFactory.newInstance(repositorySession, repository)!!

        val getTask = GetTask(URI.create(location))
        getTask.listener = CordaTransportListener(session)

        try {
            transporter.get(getTask)
        } catch (ex : Exception) {
            logger.info("Data transfer failed", ex)
            throw FlowException("Data transfer failed", ex)
        }

        session.send(TransportFinishedEvent())
    }
}

class CordaTransportListener(val session : FlowSession) : TransportListener() {
    @Suspendable
    override fun transportStarted(dataOffset : Long, dataLength : Long) {
        session.send(TransportStartedEvent(dataOffset, dataLength))
    }

    @Suspendable
    override fun transportProgressed(data : ByteBuffer?) {
        session.send(TransportProgressedEvent(data))
    }
}

@CordaSerializable
data class TransportStartedEvent(val dataOffset : Long, val dataLength : Long)

@CordaSerializable
data class TransportProgressedEvent(val data : ByteBuffer?)

@CordaSerializable
class TransportFinishedEvent

object TaskHolder {
    val tasks = mutableMapOf<Int, TransportTask>()

    fun getTask(hash : Int) = tasks[hash]!!
}

/*


//        val payloadLength = bnoSession.sendAndReceive<Int>(getTask).unwrap { it }
//        val byteArray = ByteArray(payloadLength)
//        var position = 0
//        while (position < payloadLength) {
//            val receivedBytes = bnoSession.receive<ByteArray>().unwrap { it }
//            System.arraycopy(receivedBytes, 0, byteArray, position, receivedBytes.size)
//            position += receivedBytes.size
//        }
//        return ByteInputStream(byteArray, byteArray.size)










//        val repositoryService = serviceHub.cordaService(RepositoryService::class.java)
//        val dependencyResult : DependencyResult
//        try {
//            dependencyResult = repositoryService.cordaMavenResolver.downloadVersion(mavenCoords)
//        } catch (ex : DependencyResolutionException) {
//            throw FlowException("Unable to resolve dependency $mavenCoords", ex)
//        }
//
//
//        // we are assuming that cordapp is a fat jar and hence dependency result should always contain a single artifact
//        val bytes = Files.readAllBytes(dependencyResult.artifactResults.single().artifact.file.toPath())!!
//        val maxMessageSizeBytes = serviceHub.networkParameters.maxMessageSize
//
//        // starting the transfer
//        session.send(bytes.size)
//        for (i in 0..bytes.size step maxMessageSizeBytes) {
//            val chunk = Arrays.copyOfRange(bytes, i, Math.min(i + maxMessageSizeBytes, bytes.size ))
//            session.send(chunk)
//        }

 */
