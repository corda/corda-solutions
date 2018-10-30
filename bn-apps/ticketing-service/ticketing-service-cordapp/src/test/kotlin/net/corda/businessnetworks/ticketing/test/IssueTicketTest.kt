package net.corda.businessnetworks.ticketing.test

import net.corda.businessnetworks.membership.NotAMemberException
import net.corda.businessnetworks.ticketing.NotBNOException
import net.corda.businessnetworks.ticketing.contracts.Ticket
import net.corda.businessnetworks.ticketing.contracts.TicketStatus
import net.corda.businessnetworks.ticketing.flows.bno.ActivateTicketByLinearIdFlow
import net.corda.businessnetworks.ticketing.flows.member.RequestTargetedTicketFlow
import net.corda.businessnetworks.ticketing.flows.member.RequestTicketFlow
import net.corda.businessnetworks.ticketing.flows.member.RequestWideTicketFlow
import net.corda.core.flows.FlowException
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import org.junit.Test
import kotlin.test.assertEquals

class IssueTicketTest : TicketingServiceTestsSupport() {

    @Test(expected = NotAMemberException::class)
    fun `Party has to be a member to be able to ask for ticket`() {
        createNetworkAndRunTest(1, false ) {
            val participantNode = participantNodes.first()

            val future = participantNode.startFlow(RequestWideTicketFlow(TestTicketSubject.SUBJECT_1))
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
                val tickets = participantNode.services.vaultService.queryBy<Ticket.State<*>>().states
                assertEquals(1, tickets.size)
                assertEquals(TicketStatus.PENDING, tickets.single().state.data.status)
            }

            bnoNode.transaction {
                val tickets = bnoNode.services.vaultService.queryBy<Ticket.State<*>>().states
                assertEquals(1, tickets.size)
                assertEquals(TicketStatus.PENDING, tickets.single().state.data.status)
            }
        }
    }

    @Test
    fun `Member can ask for a targeted ticket`() {
        createNetworkAndRunTest(2, true ) {
            val participantNode = participantNodes.first()
            val targetedParty = participantNodes[1].party()

            val future = participantNode.startFlow(RequestTargetedTicketFlow("Subject 1", listOf(targetedParty)))
            mockNetwork.runNetwork()
            future.getOrThrow()

            participantNode.transaction {
                val tickets = participantNode.services.vaultService.queryBy<Ticket.TargetedTicket<*>>().states
                assertEquals(1, tickets.size)
                assertEquals(TicketStatus.PENDING, tickets.single().state.data.status)
                assertEquals(listOf(targetedParty),tickets.single().state.data.appliesTo)
            }

            bnoNode.transaction {
                val tickets = bnoNode.services.vaultService.queryBy<Ticket.TargetedTicket<*>>().states
                assertEquals(1, tickets.size)
                assertEquals(TicketStatus.PENDING, tickets.single().state.data.status)
                assertEquals(listOf(targetedParty),tickets.single().state.data.appliesTo)
            }
        }
    }

    @Test(expected = FlowException::class)
    fun `BNO won't sign request if they are not the BNO on the ticket`() {
        createNetworkAndRunTest(2, true ) {
            val participantNode = participantNodes.first()
            val maliciousNode = participantNodes[1]

            val ticket = Ticket.WideTicket(participantNode.party(),maliciousNode.party(),"Subject 1")
            val future = participantNode.startFlow(RequestTicketFlow(ticket))
            mockNetwork.runNetwork()
            future.getOrThrow()
        }
    }

    @Test(expected = FlowException::class)
    fun `BNO won't sign request if the initiator is not the proposed holder`() {
        createNetworkAndRunTest(2, true ) {
            val participantNode = participantNodes.first()
            val maliciousNode = participantNodes[1]

            val ticket = Ticket.WideTicket(maliciousNode.party(),bnoNode.party(),"Subject 1")
            val future = participantNode.startFlow(RequestTicketFlow(ticket))
            mockNetwork.runNetwork()
            future.getOrThrow()
        }
    }

    @Test
    fun `Ticket can be activated`() {
        createNetworkAndRunTest(1, true ) {
            val participantNode = participantNodes.first()

            var future = participantNode.startFlow(RequestWideTicketFlow("Subject 1"))
            mockNetwork.runNetwork()
            future.getOrThrow()
            lateinit var ticketId : String

            participantNode.transaction {
                val tickets = participantNode.services.vaultService.queryBy<Ticket.State<*>>().states
                assertEquals(1, tickets.size)
                assertEquals(TicketStatus.PENDING, tickets.single().state.data.status)
                ticketId = tickets.single().state.data.linearId.toString()
            }

            bnoNode.transaction {
                val tickets = bnoNode.services.vaultService.queryBy<Ticket.State<*>>().states
                assertEquals(1, tickets.size)
                assertEquals(TicketStatus.PENDING, tickets.single().state.data.status)
            }

            future = bnoNode.startFlow(ActivateTicketByLinearIdFlow(ticketId))
            mockNetwork.runNetwork()
            future.getOrThrow()

            participantNode.transaction {
                val tickets = participantNode.services.vaultService.queryBy<Ticket.State<*>>().states
                assertEquals(1, tickets.size)
                assertEquals(TicketStatus.ACTIVE, tickets.single().state.data.status)
            }

            bnoNode.transaction {
                val tickets = bnoNode.services.vaultService.queryBy<Ticket.State<*>>().states
                assertEquals(1, tickets.size)
                assertEquals(TicketStatus.ACTIVE, tickets.single().state.data.status)
            }
        }
    }

    @Test(expected = NotBNOException::class)
    fun `Member can't activate their own ticket`() {
        createNetworkAndRunTest(1, true ) {
            val participantNode = participantNodes.first()

            var future = participantNode.startFlow(RequestWideTicketFlow("Subject 1"))
            mockNetwork.runNetwork()
            future.getOrThrow()
            lateinit var ticketId : String

            participantNode.transaction {
                val tickets = participantNode.services.vaultService.queryBy<Ticket.State<*>>().states
                assertEquals(1, tickets.size)
                assertEquals(TicketStatus.PENDING, tickets.single().state.data.status)
                ticketId = tickets.single().state.data.linearId.toString()
            }

            bnoNode.transaction {
                val tickets = bnoNode.services.vaultService.queryBy<Ticket.State<*>>().states
                assertEquals(1, tickets.size)
                assertEquals(TicketStatus.PENDING, tickets.single().state.data.status)
            }

            future = participantNode.startFlow(ActivateTicketByLinearIdFlow(ticketId))
            mockNetwork.runNetwork()
            future.getOrThrow()
        }
    }


}