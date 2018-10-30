package net.corda.businessnetworks.ticketing.test

import net.corda.businessnetworks.ticketing.TriggeringThisFlowRequiresTicket
import net.corda.businessnetworks.ticketing.contracts.Ticket
import net.corda.businessnetworks.ticketing.contracts.TicketStatus
import net.corda.businessnetworks.ticketing.flows.bno.ActivateTicketByLinearIdFlow
import net.corda.businessnetworks.ticketing.flows.member.RequestTicketFlow
import net.corda.businessnetworks.ticketing.test.flows.TestInitiator
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.StartedMockNode
import org.junit.Test
import kotlin.test.assertEquals

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
            val initiatingNode = participantNodes[0]
            val anotherMemberNode = participantNodes[1]
            runGuineaPigFlow(initiatingNode, anotherMemberNode)
        }
    }

    @Test
    fun `Flow triggers if ticket exists (wide ticket)`() {
        createNetworkAndRunTest(2, true ) {
            val ticketHoldingNode = participantNodes[0]
            acquireAWideTicket(ticketHoldingNode, TestTicketSubject.SUBJECT_1)

            val anotherMemberNode = participantNodes[1]
            runGuineaPigFlow(ticketHoldingNode, anotherMemberNode)
        }
    }

    @Test
    fun `Flow triggers if ticket exists (targeted ticket)`() {
        createNetworkAndRunTest(2, true ) {
            val ticketHoldingNode = participantNodes[0]
            val anotherMemberNode = participantNodes[1]

            acquireTargetedTicket(ticketHoldingNode, anotherMemberNode.party(), TestTicketSubject.SUBJECT_1)

            runGuineaPigFlow(ticketHoldingNode, anotherMemberNode)
        }
    }

    @Test(expected = TriggeringThisFlowRequiresTicket::class)
    fun `The owned ticket must have the required subject`() {
        createNetworkAndRunTest(2, true ) {
            val ticketHoldingNode = participantNodes[0]
            acquireAWideTicket(ticketHoldingNode, TestTicketSubject.SUBJECT_2) //granting ticket but different subject to the one required

            val anotherMemberNode = participantNodes[1]
            runGuineaPigFlow(ticketHoldingNode, anotherMemberNode)
        }
    }

    @Test(expected = TriggeringThisFlowRequiresTicket::class)
    fun `The owned targeted ticket must apply to the right member`() {
        createNetworkAndRunTest(3, true ) {
            val ticketHoldingNode = participantNodes[0]
            val anotherMemberNode = participantNodes[1]
            val targetedMemberNode = participantNodes[2]

            acquireTargetedTicket(ticketHoldingNode, targetedMemberNode.party(), TestTicketSubject.SUBJECT_1) //granting ticket targeted at one member

            runGuineaPigFlow(ticketHoldingNode, anotherMemberNode) //this triggers a flow on another member
        }
    }

    private fun <T> acquireTargetedTicket(memberNode : StartedMockNode, target : Party, subject : T) {
        val ticket = Ticket.TargetedTicket(memberNode.party(), bnoNode.party(), subject, listOf(target))
        acquireATicket(memberNode, ticket)
    }

    private fun <T> acquireAWideTicket(memberNode : StartedMockNode, subject : T) {
        val ticket = Ticket.WideTicket(memberNode.party(), bnoNode.party(), subject)
        acquireATicket(memberNode, ticket)
    }

    private fun <T> acquireATicket(memberNode : StartedMockNode, ticket : Ticket.State<T>) {
        var future = memberNode.startFlow(RequestTicketFlow(ticket))
        mockNetwork.runNetwork()
        future.getOrThrow()

        lateinit var ticketId : String

        bnoNode.transaction {
            val tickets = bnoNode.services.vaultService.queryBy<Ticket.State<*>>().states
            assertEquals(1, tickets.size)
            assertEquals(TicketStatus.PENDING, tickets.single().state.data.status)
            ticketId = tickets.single().state.data.linearId.toString()
        }

        future = bnoNode.startFlow(ActivateTicketByLinearIdFlow(ticketId))
        mockNetwork.runNetwork()
        future.getOrThrow()
    }

    private fun runGuineaPigFlow(initiator : StartedMockNode, initiatee : StartedMockNode) : String {
        val future = initiator.startFlow(TestInitiator(initiatee.party()))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }


}