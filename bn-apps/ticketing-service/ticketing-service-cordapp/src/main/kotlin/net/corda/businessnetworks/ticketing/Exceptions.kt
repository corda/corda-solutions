package net.corda.businessnetworks.ticketing

import net.corda.businessnetworks.ticketing.contracts.Ticket
import net.corda.core.contracts.StateRef
import net.corda.core.flows.FlowException

class NotBNOException(val ticket : Ticket.State<*>) : FlowException("This node is not the business network operator of this ticket")
class TicketNotFound(val linearId : String? = null, val stateRef: StateRef? = null) : FlowException("Ticket not found. Searched for linear id: $linearId, state ref: $stateRef.")
class MultipleTicketsFound(val linearId : String?, val stateRef: StateRef? = null, val found : Int) : FlowException("Multiple tickets ($found). Searched for linear id: $linearId, state ref: $stateRef.")
class TriggeringThisFlowRequiresTicket(val ticketSubject : Any) : FlowException("Triggering this flow requires this ticket: ${ticketSubject}")