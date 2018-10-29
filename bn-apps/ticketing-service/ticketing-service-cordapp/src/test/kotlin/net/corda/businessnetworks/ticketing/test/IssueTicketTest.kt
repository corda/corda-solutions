package net.corda.businessnetworks.ticketing.test

import net.corda.businessnetworks.membership.NotAMemberException
import net.corda.businessnetworks.ticketing.flows.RequestWideTicketFlow
import net.corda.core.utilities.getOrThrow
import org.junit.Test

class IssueTicketTest : BusinessNetworksTestsSupport() {

    override fun registerFlows() {

    }

    @Test(expected = NotAMemberException::class)
    fun `Party has to be a member to be able to ask for ticket`() {
        createNetworkAndRunTest(1 ) {
            val participantNode = participantNodes.first()

            val future = participantNode.startFlow(RequestWideTicketFlow("Subject 1"))
            mockNetwork.runNetwork()
            future.getOrThrow()
        }
    }


}