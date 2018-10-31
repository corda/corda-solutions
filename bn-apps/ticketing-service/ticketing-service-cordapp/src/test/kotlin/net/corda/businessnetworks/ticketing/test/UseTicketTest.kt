package net.corda.businessnetworks.ticketing.test

import net.corda.businessnetworks.ticketing.TriggeringThisFlowRequiresTicket
import net.corda.businessnetworks.ticketing.contracts.Ticket
import org.junit.Test

class UseTicketTest : TicketingServiceTestsSupport() {

    @Test(expected = TriggeringThisFlowRequiresTicket::class)
    fun `A flow won't trigger without a ticket`() {
        createNetworkAndRunTest(2, true ) {
            val initiatingNode = participantNodes[0]
            val anotherMemberNode = participantNodes[1]
            runGuineaPigFlow(initiatingNode, anotherMemberNode)
        }
    }

    @Test(expected = TriggeringThisFlowRequiresTicket::class)
    fun `A flow won't trigger without an active ticket`() {
        createNetworkAndRunTest(2, true ) {
            val ticketHoldingNode = participantNodes[0]
            val ticket = Ticket.WideTicket(ticketHoldingNode.party(), bnoNode.party(), TestTicketSubject.SUBJECT_1)
            requestATicket(ticketHoldingNode, ticket)

            val anotherMemberNode = participantNodes[1]
            runGuineaPigFlow(ticketHoldingNode, anotherMemberNode)
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




}