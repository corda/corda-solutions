package net.corda.businessnetworks.ticketing.test

import net.corda.businessnetworks.ticketing.TriggeringThisFlowRequiresTicket
import net.corda.businessnetworks.ticketing.contracts.Ticket
import net.corda.businessnetworks.ticketing.flows.member.RequestTicketFlow
import net.corda.businessnetworks.ticketing.test.flows.TestInitiator
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.StartedMockNode
import org.junit.Test

class UseTicketTest : BusinessNetworksTestsSupport(listOf("net.corda.businessnetworks.ticketing.contracts",
                                                            "net.corda.businessnetworks.ticketing.flows.member",
                                                            "net.corda.businessnetworks.ticketing.test.flows")) {

    @CordaSerializable
    enum class TestTicketSubject {
        SUBJECT_1,
        SUBJECT_2
    }

    @Test(expected = TriggeringThisFlowRequiresTicket::class)
    fun `A flow won't trigger without a ticket`() {
        createNetworkAndRunTest(2, true ) {
            val ticketHoldingNode = participantNodes[0]
            val anotherMemberNode = participantNodes[1]
            runGuineaPigFlow(ticketHoldingNode, anotherMemberNode)
        }
    }

    private fun <T> acquireAWideTicket(memberNode : StartedMockNode, subject : T) {
        val ticket = Ticket.WideTicket(memberNode.party(), bnoNode.party(), subject)
        acquireATicket(memberNode, ticket)
    }

    private fun <T> acquireATicket(memberNode : StartedMockNode, ticket : Ticket.State<T>) {
        val future = memberNode.startFlow(RequestTicketFlow(ticket))
        mockNetwork.runNetwork()
        future.getOrThrow()
    }

    private fun runGuineaPigFlow(initiator : StartedMockNode, initiatee : StartedMockNode) : String {
        val future = initiator.startFlow(TestInitiator(initiatee.party()))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }


}