package net.corda.businessnetworks.ticketing.test

import net.corda.businessnetworks.membership.NotAMemberException
import net.corda.businessnetworks.ticketing.contracts.Ticket
import net.corda.businessnetworks.ticketing.flows.RequestWideTicketFlow
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import org.junit.Test
import kotlin.test.assertEquals

class IssueTicketTest : BusinessNetworksTestsSupport(listOf("net.corda.businessnetworks.ticketing.contracts",
                                                            "net.corda.businessnetworks.ticketing.flows")) {

    @Test(expected = NotAMemberException::class)
    fun `Party has to be a member to be able to ask for ticket`() {
        createNetworkAndRunTest(1, false ) {
            val participantNode = participantNodes.first()

            val future = participantNode.startFlow(RequestWideTicketFlow("Subject 1"))
            mockNetwork.runNetwork()
            future.getOrThrow()
        }
    }

    @Test
    fun `Member can ask for a wide ticket`() {
        createNetworkAndRunTest(1, true ) {
            val participantNode = participantNodes.first()

            val future = participantNode.startFlow(RequestWideTicketFlow("Subject 1"))
            mockNetwork.runNetwork()
            future.getOrThrow()

            participantNode.transaction {
                val tickets = participantNode.services.vaultService.queryBy<Ticket.State>().states
                assertEquals(1, tickets.size)
            }
        }
    }


}