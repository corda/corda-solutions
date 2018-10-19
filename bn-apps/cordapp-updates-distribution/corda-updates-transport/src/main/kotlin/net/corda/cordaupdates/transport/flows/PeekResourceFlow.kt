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

/**
 * Peeks resource in a remote repository. Used internally by Maven Resolver.
 *
 * @param resourceLocation location of the resource provided by Maven Resolver
 * @param repoHosterName x500Name of the repository hoster
 */
@InitiatingFlow
@StartableByRPC
@StartableByService
class PeekResourceFlow(private val resourceLocation : String, private val repoHosterName : String) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val repoHosterParty = serviceHub.identityService.wellKnownPartyFromX500Name(CordaX500Name.parse(repoHosterName))!!
        val repoHosterSession = initiateFlow(repoHosterParty)
        repoHosterSession.sendAndReceive<Boolean>(resourceLocation).unwrap { it }
    }
}

/**
 * This flows should exist at repository hoster's node. The flow peeks an artifact in a configured file- or http(s)- based repository.
 *
 * The flow supports [SessionFilter]s to restrict unauthorised traffic.
 */
@InitiatedBy(PeekResourceFlow::class)
class PeekResourceFlowResponder(session : FlowSession) : AbstractRepositoryHosterResponder<Unit>(session) {
    @Suspendable
    override fun doCall() {
        val location = session.receive<String>().unwrap { it }
        val configuration = serviceHub.cordaService(RepositoryHosterConfigurationService::class.java)
        val repository = RemoteRepository.Builder("remote", "default", configuration.remoteRepoUrl()).build()

        val transporterFactory = transporterFactory(repository.protocol)
        val repositorySession = MavenRepositorySystemUtils.newSession()

        val transporter = transporterFactory.newInstance(repositorySession, repository)!!

        try {
            transporter.peek(PeekTask(URI.create(location)))
        } catch (ex : Exception) {
            throw toCordaException(ex, transporter)
        }
        session.send(true)
    }
}