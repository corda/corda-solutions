package net.corda.businessnetworks.cordaupdates.core.flowstransport

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.cordaupdates.core.flowstransport.Utils.transporterFactory
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.utilities.unwrap
import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.spi.connector.transport.PeekTask
import java.lang.Exception

@InitiatingFlow
class PeekArtifactFlow(val peekTask : PeekTask) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val configuration = serviceHub.cordaService(NodeConfigurationService::class.java)
        val bnoSession = initiateFlow(configuration.bnoParty())
        bnoSession.sendAndReceive<Boolean>(peekTask).unwrap { it }
    }
}

@InitiatedBy(PeekArtifactFlow::class)
class PeekArtifactFlowResponder(val session : FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val peekTask = session.receive<PeekTask>().unwrap { it }

        val transporterFactory = transporterFactory(serviceHub)
        val configuration = serviceHub.cordaService(BNOConfigurationService::class.java)
        val repository = RemoteRepository.Builder("remote", "default", configuration.remoteRepoUrl()).build()
        val repositorySession = MavenRepositorySystemUtils.newSession()

        val transporter = transporterFactory.newInstance(repositorySession, repository)!!

        try {
            transporter.peek(peekTask)
        } catch (ex : Exception) {
            logger.info("Exception while peeking $peekTask", ex)
            throw FlowException("Exception while peeking", ex)
        }

        session.send(true)
    }
}