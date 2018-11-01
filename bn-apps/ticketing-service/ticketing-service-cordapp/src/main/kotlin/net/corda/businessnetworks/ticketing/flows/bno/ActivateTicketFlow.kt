package net.corda.businessnetworks.ticketing.flows.bno

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.bno.support.BusinessNetworkOperatorFlowLogic
import net.corda.businessnetworks.ticketing.MultipleTicketsFound
import net.corda.businessnetworks.ticketing.NotBNOException
import net.corda.businessnetworks.ticketing.TicketNotFound
import net.corda.businessnetworks.ticketing.contracts.Ticket
import net.corda.businessnetworks.ticketing.contracts.TicketStatus
import net.corda.businessnetworks.ticketing.getTicketStateAndRef
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

@StartableByRPC
class ActivateTicketByLinearIdFlow(val linearId : String) : FlowLogic<SignedTransaction>() {

    companion object {
        object LOOKING_FOR_THE_TICKET : ProgressTracker.Step("Looking for the ticket in vault")
        object ACTIVATING_THE_TICKET : ProgressTracker.Step("Activating the ticket")


        fun tracker() = ProgressTracker(
                LOOKING_FOR_THE_TICKET,
                ACTIVATING_THE_TICKET
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = LOOKING_FOR_THE_TICKET
        val ticket = serviceHub.vaultService.getTicketStateAndRef(linearId)

        progressTracker.currentStep = ACTIVATING_THE_TICKET
        return subFlow(ActivateTicketFlow(ticket))
    }
}

@StartableByRPC
class ActivateTicketFlow(val ticketStateAndRef : StateAndRef<Ticket.State<*>>) : BusinessNetworkOperatorFlowLogic<SignedTransaction>() {

    companion object {
        object VERIFYING_TICKET : ProgressTracker.Step("Verifying the ticket")
        object CREATING_TRANSACTION : ProgressTracker.Step("Creating transaction")
        object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction")
        object FINALISING_TRANSACTION : ProgressTracker.Step("Finalising transaction")


        fun tracker() = ProgressTracker(
                VERIFYING_TICKET,
                CREATING_TRANSACTION,
                SIGNING_TRANSACTION,
                FINALISING_TRANSACTION
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call() : SignedTransaction {
        progressTracker.currentStep = VERIFYING_TICKET
        verifyTicket(ticketStateAndRef.state.data)

        progressTracker.currentStep = CREATING_TRANSACTION
        val notary = getNotary()
        val transactionBuilder = createTransaction(notary)

        progressTracker.currentStep = SIGNING_TRANSACTION
        val signedByUs = serviceHub.signInitialTransaction(transactionBuilder)

        progressTracker.currentStep = FINALISING_TRANSACTION
        return subFlow(FinalityFlow(signedByUs))
    }

    private fun verifyTicket(ticket : Ticket.State<*>) {
        if(ticket.bno != ourIdentity) {
            throw NotBNOException(ticket)
        }
        //don't verify anything the contract will do
    }

    private fun createTransaction(notary : Party) : TransactionBuilder {
        val ticketState = ticketStateAndRef.state.data
        val transactionBuilder = TransactionBuilder(notary)
        transactionBuilder.addInputState(ticketStateAndRef)
        transactionBuilder.addOutputState(ticketState.withNewStatus(TicketStatus.ACTIVE),Ticket.CONTRACT_NAME)
        transactionBuilder.addCommand(Ticket.Commands.Activate(),ticketState.bno.owningKey)
        transactionBuilder.verify(serviceHub)
        return transactionBuilder
    }

}
