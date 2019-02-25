package com.r3.businessnetworks.cordaupdates.transport.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.cordaupdates.transport.flows.Utils.toCordaException
import com.r3.businessnetworks.cordaupdates.transport.flows.Utils.transporterFactory
import net.corda.core.flows.FlowException
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

/**
 * Fetches a resource from a remote repository. Used internally by Maven Resolver.
 *
 * @param resourceLocation resource location provided by Maven Resolver
 * @param repositoryHosterName x500Name of the repository hoster
 * @param repositoryName name of the repository to get the resource from. Repository with this name should be configured on the other side.
 */
@StartableByService
@StartableByRPC
@InitiatingFlow
class GetResourceFlow(private val resourceLocation : String, private val repositoryHosterName : String, private val repositoryName : String) : FlowLogic<ByteArray>() {
    @Suspendable
    override fun call() : ByteArray {
        val repoHosterParty = serviceHub.identityService.wellKnownPartyFromX500Name(CordaX500Name.parse(repositoryHosterName))
                ?: throw FlowException("Party $repositoryHosterName can't be found on the network")

        val repoHosterSession = initiateFlow(repoHosterParty)

        // maximum allowed length of payload is 10 times maxTransactionSize
        val maxAllowedPayloadLength = 10 * serviceHub.networkParameters.maxMessageSize

        val payloadLength = repoHosterSession.sendAndReceive<Int>(ResourceRequest(repositoryName, resourceLocation)).unwrap {
            if (it < 0 || it > maxAllowedPayloadLength) {
                throw FlowException("Payload length should be greater than 0 and less than $maxAllowedPayloadLength")
            }
            it
        }

        val byteArray = ByteArray(payloadLength)
        var position = 0
        while (position < payloadLength) {
            val receivedBytes = repoHosterSession.receive<ByteArray>().unwrap { it }
            if (receivedBytes.isEmpty()) {
                throw FlowException("Received an empty byte array")
            }
            System.arraycopy(receivedBytes, 0, byteArray, position, receivedBytes.size)
            position += receivedBytes.size
        }
        return byteArray
    }
}

/**
 * This flows should exist at the repository hoster's node. The flow fetches an artifact from a configured file- or http(s)- based repository,
 * splits it into chunks of [serviceHub.networkParameters.maxMessageSize] size and sends back to the requester.
 *
 * The flow supports [SessionFilter]s to restrict unauthorised traffic.
 */
@InitiatedBy(GetResourceFlow::class)
open class GetResourceFlowResponder(session : FlowSession) : AbstractRepositoryHosterResponder<Unit>(session) {
    @Suspendable
    override fun postPermissionCheck() {
        val request = session.receive<ResourceRequest>().unwrap {
            // make sure that the request contains only allowed character set
            Utils.verifyMavenResourceURI(it.resourceLocation)
            it
        }

        val bytes = getArtifactBytes(request.repositoryName, request.resourceLocation)

        val maxMessageSizeBytes = serviceHub.networkParameters.maxMessageSize

        session.send(bytes.size)
        for (i in 0..bytes.size step maxMessageSizeBytes) {
            val chunk = Arrays.copyOfRange(bytes, i, Math.min(i + maxMessageSizeBytes, bytes.size ))
            session.send(chunk)
        }
    }

    private fun getArtifactBytes(repositoryName : String, location : String) : ByteArray {
        val getTask = GetTask(URI.create(location))
        val transporter = createTransporter(repositoryName)
        try {
            transporter.get(getTask)
        } catch (ex : Exception) {
            logger.info("Error getting resource $location from repository $repositoryName", ex)
            throw toCordaException(ex, transporter, repositoryName, location)
        }
        return getTask.dataBytes
    }

    private fun createTransporter(repositoryName : String) : Transporter {
        val configuration = serviceHub.cordaService(RepositoryHosterConfigurationService::class.java)
        val repository = RemoteRepository.Builder("remote", "default", configuration.repositoryUrl(repositoryName)).build()
        val transporterFactory = transporterFactory(repository.protocol)
        val repositorySession = newSession()
        return transporterFactory.newInstance(repositorySession, repository)!!
    }
}