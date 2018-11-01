package net.corda.businessnetworks.ticketing

import net.corda.businessnetworks.ticketing.contracts.Ticket
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria

// -------- extensions on vault service ----------

fun VaultService.getTicketStateAndRef(linearId : String) : StateAndRef<Ticket.State<*>> {
    val criteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(UniqueIdentifier.fromString(linearId)))
    val tickets = this.queryBy<Ticket.State<*>>(criteria).states
    return when {
        tickets.isEmpty() -> throw TicketNotFound(linearId, null)
        tickets.size > 1 -> throw MultipleTicketsFound(linearId, null, tickets.size)
        else -> tickets.single()
    }
}

fun VaultService.getTicketStateAndRef(ticketStateRef : StateRef) : StateAndRef<Ticket.State<*>> {
    val criteria = QueryCriteria.VaultQueryCriteria(stateRefs = listOf(ticketStateRef))
    val tickets = this.queryBy<Ticket.State<*>>(criteria).states
    return when {
        tickets.isEmpty() -> throw TicketNotFound(null, ticketStateRef)
        tickets.size > 1 -> throw MultipleTicketsFound(null, ticketStateRef, tickets.size)
        else -> tickets.single()
    }
}