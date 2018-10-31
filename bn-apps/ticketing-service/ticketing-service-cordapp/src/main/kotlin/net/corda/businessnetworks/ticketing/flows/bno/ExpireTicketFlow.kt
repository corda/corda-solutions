package net.corda.businessnetworks.ticketing.flows.bno

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.ticketing.MultipleTicketsFound
import net.corda.businessnetworks.ticketing.TicketNotFound
import net.corda.businessnetworks.ticketing.contracts.Ticket
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.ProgressTracker

@SchedulableFlow
@StartableByRPC
class ExpireTicketFromSchedulerFlow(val ticketStateRef: StateRef) : FlowLogic<Unit>() {

    companion object {
        object LOOKING_FOR_THE_TICKET : ProgressTracker.Step("Looking for the ticket in vault")
        object REVOKING_THE_TICKET_IF_WE_ARE_THE_BNO : ProgressTracker.Step("Revoking the ticket if we are the BNO")


        fun tracker() = ProgressTracker(
                LOOKING_FOR_THE_TICKET,
                REVOKING_THE_TICKET_IF_WE_ARE_THE_BNO
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call() {
        progressTracker.currentStep = LOOKING_FOR_THE_TICKET
        val ticket = getTicketStateAndRef()

        progressTracker.currentStep = REVOKING_THE_TICKET_IF_WE_ARE_THE_BNO
        if(ticket.state.data.bno != ourIdentity) {
            logger.info("Not being the BNO we are ignoring this scheduled expiry")
            return
        } else {
            logger.info("As a BNO we are revoking the ticket")
            subFlow(RevokeTicketFlow(ticket))
        }

    }

    private fun getTicketStateAndRef() : StateAndRef<Ticket.State<*>> {
        val criteria = QueryCriteria.VaultQueryCriteria(stateRefs = listOf(ticketStateRef))
        val tickets = serviceHub.vaultService.queryBy<Ticket.State<*>>(criteria).states
        return when {
            tickets.isEmpty() -> throw TicketNotFound(null, ticketStateRef)
            tickets.size > 1 -> throw MultipleTicketsFound(null, ticketStateRef, tickets.size)
            else -> tickets.single()
        }
    }
}


