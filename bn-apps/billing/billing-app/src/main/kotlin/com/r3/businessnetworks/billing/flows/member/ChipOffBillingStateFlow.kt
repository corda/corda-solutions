package com.r3.businessnetworks.billing.flows.member

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.billing.states.BillingChipState
import com.r3.businessnetworks.billing.states.BillingContract
import com.r3.businessnetworks.billing.states.BillingState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

class ChipOffBillingStateFlow(private val billingState : StateAndRef<BillingState>,
                              private val amount : Long) : FlowLogic<Pair<BillingChipState, SignedTransaction>>() {
    @Suspendable
    override fun call() : Pair<BillingChipState, SignedTransaction> {
        val (outputBillingState, chipState) = billingState.state.data.chipOff(amount)
        val notary = serviceHub.networkMapCache.notaryIdentities.single()
        val builder = TransactionBuilder(notary)
                .addInputState(billingState)
                .addOutputState(outputBillingState, BillingContract.CONTRACT_NAME)
                .addOutputState(chipState, BillingContract.CONTRACT_NAME)
                .addCommand(BillingContract.Commands.ChipOff(), chipState.owner.owningKey)

        builder.verify(serviceHub)

        val session = initiateFlow(billingState.state.data.issuer)

        val stx = serviceHub.signInitialTransaction(builder)
        val notarisedTx = subFlow(FinalityFlow(stx, listOf(session)))
        return Pair(chipState, notarisedTx)
    }
}