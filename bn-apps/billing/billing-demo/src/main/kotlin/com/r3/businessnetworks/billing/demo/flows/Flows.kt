package com.r3.businessnetworks.billing.demo.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.billing.demo.contracts.SampleContract
import com.r3.businessnetworks.billing.demo.contracts.SampleState
import com.r3.businessnetworks.billing.flows.member.ChipOffBillingStateFlow
import com.r3.businessnetworks.billing.flows.member.service.MemberBillingDatabaseService
import com.r3.businessnetworks.billing.states.BillingChipState
import com.r3.businessnetworks.billing.states.BillingContract
import com.r3.businessnetworks.billing.states.BillingState
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.ReceiveStateAndRefFlow
import net.corda.core.flows.SendStateAndRefFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

/**
 * Flow to issue a SampleState on the ledger
 */
class IssueSampleStateFlow : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call() : SignedTransaction {
        // for the sake of demo we are just taking the first available notary
        val notary = serviceHub.networkMapCache.notaryIdentities.single()
        val builder = TransactionBuilder(notary)
                .addOutputState(SampleState(ourIdentity), SampleContract.CONTRACT_NAME)
                .addCommand(SampleContract.Commands.Issue(), ourIdentity.owningKey)

        // chipping off billing state and adding required primitives to the builder
        chipOffAndAddToBuilder(builder, this)

        builder.verify(serviceHub)
        val stx = serviceHub.signInitialTransaction(builder)
        return subFlow(FinalityFlow(stx, listOf()))
    }
}

@CordaSerializable
class RequestBillingChipForTransaction

/**
 * Flow to transfer SampleState between the owners
 */
@InitiatingFlow
class TransferSampleStateFlow(val sampleState : StateAndRef<SampleState>,
                              val partyToTransferTo : Party) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call() : SignedTransaction {
        if (ourIdentity != sampleState.state.data.owner)
            throw FlowException("Only owner of the state can transfer it")
        if (ourIdentity == partyToTransferTo)
            throw FlowException("sender and recipient should be different parties")
        // for the sake of demo we are just taking the first available notary
        val notary = serviceHub.networkMapCache.notaryIdentities.single()
        val builder = TransactionBuilder(notary)
                .addInputState(sampleState)
                .addOutputState(SampleState(partyToTransferTo))
                .addCommand(SampleContract.Commands.Transfer(), ourIdentity.owningKey, partyToTransferTo.owningKey)

        // adding our billing chip to the builder
        chipOffAndAddToBuilder(builder, this)
        // requesting the counterparty to provide us with their billing state and chip
        val otherPartySession = initiateFlow(partyToTransferTo)

        // requesting other party to send us their billing states
        otherPartySession.send(RequestBillingChipForTransaction())

        val counterpartBillingState = subFlow(ReceiveStateAndRefFlow<BillingState>(otherPartySession)).single()
        val counterpartBillingChipState = subFlow(ReceiveStateAndRefFlow<BillingChipState>(otherPartySession)).single()

        // adding the received states to the builder
        builder.addInputState(counterpartBillingChipState)
                .addReferenceState(ReferencedStateAndRef(counterpartBillingState))
                .addCommand(BillingContract.Commands.UseChip(partyToTransferTo), partyToTransferTo.owningKey)

        // verifying the transaction
        builder.verify(serviceHub)

        val selfSignedTx = serviceHub.signInitialTransaction(builder)
        val allSignedTx = subFlow(CollectSignaturesFlow(selfSignedTx, listOf(otherPartySession)))
        return subFlow(FinalityFlow(allSignedTx, listOf(otherPartySession)))
    }
}

@InitiatedBy(TransferSampleStateFlow::class)
class TransferSampleStateFlowResponder(val session : FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // receiving request for billing state
        session.receive<RequestBillingChipForTransaction>()

        // chipping off required amount
        val (billingState, billingChip) = chipOff(this)

        // sending the states to the requester
        subFlow(SendStateAndRefFlow(session, listOf(billingState)))
        subFlow(SendStateAndRefFlow(session, listOf(billingChip)))

        val stx = subFlow(object : SignTransactionFlow(session) {
            override fun checkTransaction(stx : SignedTransaction) {
                stx.toLedgerTransaction(serviceHub, false).verify()
            }
        })
        subFlow(ReceiveFinalityFlow(session, stx.id))
    }
}

@Suspendable
private fun chipOff(flowLogic : FlowLogic<*>) : Pair<StateAndRef<BillingState>, StateAndRef<BillingChipState>> {
    val databaseService = flowLogic.serviceHub.cordaService(MemberBillingDatabaseService::class.java)
    // getting billing state from the vault. We know that there is only one billing state in the vault for this example
    // but in the case where multiple billing states have been issued, you could use the externalId on the
    // billing state to filter out the one you require.
    val billingState = databaseService.getOurActiveBillingStates().single()
    // chipping off an amount for transaction
    val (chips, _) = flowLogic.subFlow(ChipOffBillingStateFlow(billingState, SampleContract.BILLING_CHIPS_TO_PAY, 1))
    // fetching newly issued billing chip
    val billingChip = databaseService.getBillingChipStateByLinearId(chips.single().linearId)!!
    // fetching billing state after chip off
    val billingStateAfterChipOff = databaseService.getBillingStateByLinearId(billingState.state.data.linearId)!!
    return Pair(billingStateAfterChipOff, billingChip)
}

@Suspendable
private fun chipOffAndAddToBuilder(builder : TransactionBuilder, flowLogic : FlowLogic<*>) {
    val (billingState, billingChip) = chipOff(flowLogic)
    // adding the chip as an input
    builder.addInputState(billingChip)
            // adding UseChip command
            .addCommand(BillingContract.Commands.UseChip(flowLogic.ourIdentity), flowLogic.ourIdentity.owningKey)
            // adding billing state as reference input
            .addReferenceState(ReferencedStateAndRef(billingState))
}