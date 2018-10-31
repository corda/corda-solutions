package net.corda.businessnetworks.ticketing.test

import org.junit.Test

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
        //@todo here
    }

}