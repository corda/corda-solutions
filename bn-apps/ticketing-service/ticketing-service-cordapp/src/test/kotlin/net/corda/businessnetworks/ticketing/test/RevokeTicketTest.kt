package net.corda.businessnetworks.ticketing.test

import net.corda.businessnetworks.ticketing.TriggeringThisFlowRequiresTicket
import net.corda.businessnetworks.ticketing.test.support.TicketingServiceTestsSupport
import org.junit.Test
import kotlin.test.assertFalse

class RevokeTicketTest : TicketingServiceTestsSupport() {

    @Test
    fun `A ticket can be revoked`() {
        createNetworkAndRunTest(1, true ) {
            val ticketHoldingNode = participantNodes[0]
            val ticketId = acquireAWideTicketAndConfirmAssertions(ticketHoldingNode, TestTicketSubject.SUBJECT_1)
            revokeATicketAndConfirmAssertions(ticketHoldingNode, ticketId)
        }
    }

    @Test
    fun `A revoked ticket no longer works`() {
        createNetworkAndRunTest(2, true ) {
            val ticketHoldingNode = participantNodes[0]
            val ticketId = acquireAWideTicketAndConfirmAssertions(ticketHoldingNode, TestTicketSubject.SUBJECT_1)

            val anotherMemberNode = participantNodes[1]
            runGuineaPigFlow(ticketHoldingNode, anotherMemberNode)

            revokeATicketAndConfirmAssertions(ticketHoldingNode, ticketId)
            try {
                runGuineaPigFlow(ticketHoldingNode, anotherMemberNode)
                assertFalse(true, "The above line of code should have raised an exception")
            } catch (e : TriggeringThisFlowRequiresTicket) {
                //success, this is expected
            }
        }
    }

}