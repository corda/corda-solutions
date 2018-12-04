package net.corda.businessnetworks.ticketing.test

import net.corda.businessnetworks.ticketing.TriggeringThisFlowRequiresTicket
import net.corda.businessnetworks.ticketing.test.support.TicketingServiceTestsSupport
import org.junit.Test
import java.lang.Long.max
import java.time.Instant
import kotlin.test.assertFalse

class ExpireTicketTest : TicketingServiceTestsSupport(true) {

    @Test
    fun `A ticket stops working after expiry`() {
        createNetworkAndRunTest(2, true) {
            val ticketHoldingNode = participantNodes[0]
            val now = Instant.now()
            acquireAnExpiringWideTicketAndConfirmAssertions(ticketHoldingNode, TestTicketSubject.SUBJECT_1, now.plusSeconds(20))

            val anotherMemberNode = participantNodes[1]
            runGuineaPigFlow(ticketHoldingNode, anotherMemberNode)

            //let the ticket expire, let at least 25 seconds pass from the ticket creation to running the flow
            //alternatively we could call waitQuiescent on the mock network here  as that will wait for the scheduled activity to pass
            val timeElapsed = Instant.now().epochSecond - now.epochSecond
            Thread.sleep(max(25 - timeElapsed, 0)*1000)

            try {
                runGuineaPigFlow(ticketHoldingNode, anotherMemberNode)
                assertFalse(true, "The above line of code should have raised a TriggeringThisFlowRequiresTicket exception as the ticket should be expired by now")
            } catch (e : TriggeringThisFlowRequiresTicket) {
                //success, this is expected
            }
        }
    }

}