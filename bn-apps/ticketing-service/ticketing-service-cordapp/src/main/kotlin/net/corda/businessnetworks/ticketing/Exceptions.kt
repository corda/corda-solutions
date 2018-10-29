package net.corda.businessnetworks.ticketing

import net.corda.businessnetworks.ticketing.contracts.Ticket
import net.corda.core.flows.FlowException

class NotBNOException(val ticket : Ticket.State<*>) : FlowException("This node is not the business network operator of this ticket")
class TicketNotFound(val linearId : String) : FlowException("Ticket for this linear id not found: $linearId")
class MultipleTicketsFound(val linearId : String, val found : Int) : FlowException("Multiple tickets ($found) for this linear id found: $linearId")