package net.corda.businessnetworks.ticketing.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.bno.support.BusinessNetworkOperatorInitiatedFlow
import net.corda.businessnetworks.membership.member.support.BusinessNetworkAwareFlowLogic
import net.corda.businessnetworks.membership.states.MembershipState
import net.corda.businessnetworks.ticketing.contracts.Ticket
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

@StartableByRPC
class RequestWideTicketFlow(val subject : String) : BusinessNetworkAwareFlowLogic<SignedTransaction>() {

    companion object {
        object CREATING_TICKET : ProgressTracker.Step("Creating ticket")
        object SENDING_TO_BNO : ProgressTracker.Step("Sending to BNO")


        fun tracker() = ProgressTracker(
                CREATING_TICKET,
                SENDING_TO_BNO
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = CREATING_TICKET
        val ticket = Ticket.WideTicket(ourIdentity, getBno(), subject)
        progressTracker.currentStep = SENDING_TO_BNO
        return subFlow(RequestTicketFlow(ticket))
    }
}

@StartableByRPC
@InitiatingFlow
class RequestTicketFlow(val ticket : Ticket.State) : BusinessNetworkAwareFlowLogic<SignedTransaction>() {

    companion object {
        object GETTING_NOTARY_IDENTITY : ProgressTracker.Step("Getting notary identity from BNO")
        object CREATING_TRANSACTION : ProgressTracker.Step("Creating transaction")
        object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction")
        object COLLECTING_SIGNATURES : ProgressTracker.Step("Collecting signatures")
        object FINALISING_TRANSACTION : ProgressTracker.Step("Finalising transaction")


        fun tracker() = ProgressTracker(
                GETTING_NOTARY_IDENTITY,
                CREATING_TRANSACTION,
                SIGNING_TRANSACTION,
                COLLECTING_SIGNATURES,
                FINALISING_TRANSACTION
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call() : SignedTransaction {

        progressTracker.currentStep = GETTING_NOTARY_IDENTITY
        val notary = getNotary()

        progressTracker.currentStep = CREATING_TRANSACTION
        val bno = getBno()
        val transactionBuilder = createTransaction(notary, ticket, bno)

        progressTracker.currentStep = SIGNING_TRANSACTION
        val signedByUs = serviceHub.signInitialTransaction(transactionBuilder)

        progressTracker.currentStep = COLLECTING_SIGNATURES
        val bnoSession = initiateFlow(bno)
        val signedByAll = subFlow(CollectSignaturesFlow(signedByUs, listOf(bnoSession)))

        progressTracker.currentStep = FINALISING_TRANSACTION
        return subFlow(FinalityFlow(signedByAll))
    }

    private fun createTransaction(notary : Party, ticket : Ticket.State, bno : Party) : TransactionBuilder {
        val transactionBuilder = TransactionBuilder(notary)
        transactionBuilder.addOutputState(ticket, Ticket.CONTRACT_NAME)
        transactionBuilder.addCommand(Ticket.Commands.Request(),ourIdentity.owningKey, bno.owningKey)
        transactionBuilder.verify(serviceHub)
        return transactionBuilder
    }

}

@InitiatedBy(RequestTicketFlow::class)
class RequestTicketFlowResponder(flowSession : FlowSession) : BusinessNetworkOperatorInitiatedFlow<Unit>(flowSession) {

    companion object {
        object CHECKING_TRANSACTION : ProgressTracker.Step("Checking transaction")

        fun tracker() = ProgressTracker(
                CHECKING_TRANSACTION
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun onCounterpartyMembershipVerified(counterpartyMembership: StateAndRef<MembershipState<Any>>) {
        val transactionSigner = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx : SignedTransaction) {
                val output = stx.tx.outputs.single()
                if (output.contract != Ticket.CONTRACT_NAME) {
                    throw FlowException("Output state has to be verified by ${Ticket.CONTRACT_NAME}")
                }

                val ticketState = output.data as Ticket.State
                if (ourIdentity != ticketState.bno) {
                    throw IllegalArgumentException("We have to be the BNO")
                }

                if (flowSession.counterparty != ticketState.holder) {
                    throw IllegalArgumentException("The initiating counterparty has to be the holder")
                }
            }
        }
        progressTracker.currentStep = CHECKING_TRANSACTION
        subFlow(transactionSigner)
    }

}

