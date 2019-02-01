package com.r3.businessnetworks.billing.flows.member

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.billing.flows.member.service.MemberConfigurationService
import com.r3.businessnetworks.billing.states.BillingChipState
import com.r3.businessnetworks.billing.states.BillingContract
import com.r3.businessnetworks.billing.states.BillingState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

/**
 * Chips off one or more states from the BillingState
 *
 * @param billingState state to chip off from
 * @param amount amount of each cheap off state
 * @param numberOfChips number of states to chip off
 */
@StartableByRPC
@InitiatingFlow
class ChipOffBillingStateFlow(private val billingState : StateAndRef<BillingState>,
                              private val chipAmount : Long,
                              private val numberOfChips : Int = 1) : FlowLogic<Pair<List<BillingChipState>, SignedTransaction>>() {
    @Suspendable
    override fun call() : Pair<List<BillingChipState>, SignedTransaction> {
        val configuration = serviceHub.cordaService(MemberConfigurationService::class.java)
        val notary = configuration.notaryParty()
        val builder = TransactionBuilder(notary)
                .addInputState(billingState)
                .addCommand(BillingContract.Commands.ChipOff(), billingState.state.data.owner.owningKey)

        // generating enough of chip off states
        var outputBillingState = billingState.state.data
        (1..numberOfChips).forEach { _ ->
            val pair = outputBillingState.chipOff(chipAmount)
            builder.addOutputState(pair.second, BillingContract.CONTRACT_NAME)
            outputBillingState = pair.first
        }
        builder.addOutputState(outputBillingState, BillingContract.CONTRACT_NAME)

        val session = initiateFlow(billingState.state.data.issuer)
        val stx = serviceHub.signInitialTransaction(builder)
        val notarisedTx = subFlow(FinalityFlow(stx, listOf(session)))
        return Pair(notarisedTx.tx.outputsOfType(), notarisedTx)
    }
}

/**
 * Tries to chip off the rest of the unspent amount from the [billingState].
 * Let's say if [billingState] unspent amount is 5 and [chipAmount] is 2 then the flow would chip off 2x2 chips and leave 1 unspent.
 * The flow is applicable only to Pre-paid billing states, with positive issued amount
 */
@StartableByRPC
class ChipOffRemainingAmountFlow(private val billingState : StateAndRef<BillingState>,
                                 private val chipAmount : Long) : FlowLogic<Pair<List<BillingChipState>, SignedTransaction>?>() {
    @Suspendable
    override fun call() : Pair<List<BillingChipState>, SignedTransaction>? {
        if (billingState.state.data.issued == 0L) {
            throw FlowException("Issued amount should be positive")
        }
        val numberOfChips = (billingState.state.data.issued - billingState.state.data.spent) / chipAmount
        if (numberOfChips <= 0) {
            return null
        }
        return subFlow(ChipOffBillingStateFlow(billingState, chipAmount, numberOfChips.toInt()))

    }
}