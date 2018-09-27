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
import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.spi.connector.transport.PeekTask
import java.lang.Exception
import java.net.URI

@InitiatingFlow
@StartableByRPC
@StartableByService
class PeekArtifactFlow(private val resourceLocation : String, private val bnoName : String) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val bnoParty = serviceHub.identityService.wellKnownPartyFromX500Name(CordaX500Name.parse(bnoName))!!
        val bnoSession = initiateFlow(bnoParty)
        bnoSession.sendAndReceive<Boolean>(resourceLocation).unwrap { it }
    }
}

@InitiatedBy(PeekArtifactFlow::class)
class PeekArtifactFlowResponder(val session : FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val location = session.receive<String>().unwrap { it }
        val configuration = serviceHub.cordaService(BNOConfigurationService::class.java)
        val repository = RemoteRepository.Builder("remote", "default", configuration.remoteRepoUrl()).build()

        val transporterFactory = transporterFactory(repository.protocol)
        val repositorySession = MavenRepositorySystemUtils.newSession()

        val transporter = transporterFactory.newInstance(repositorySession, repository)!!

        try {
            transporter.peek(PeekTask(URI.create(location)))
        } catch (ex : Exception) {
            logger.info("Error peeking resource at $location")
            throw toCordaException(ex, transporter)
        }
        session.send(true)
    }
}