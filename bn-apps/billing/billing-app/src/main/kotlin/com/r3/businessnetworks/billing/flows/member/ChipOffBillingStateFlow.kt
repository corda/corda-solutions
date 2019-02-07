package com.r3.businessnetworks.billing.flows.member

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.billing.flows.Constants.TIME_TOLERANCE
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
import java.time.Duration
import java.time.Instant

/**
 * Chips off one or more [BillingChipState]s from the [billingState]. Chipping off multiple [BillingChipState]s in one transaction
 * might be useful for performance optimisation.
 *
 * @param billingState the state to chip off from
 * @param chipAmount the amount of each [BillingChipState]
 * @param numberOfChips number of states to chip off
 * @param timeTolerance the time tolerance to be used for the transaction time window if the [billingState] has an expiry date
 * @return a list of chipped off states with the associated [SignedTransaction]
 */
@StartableByRPC
@InitiatingFlow
class ChipOffBillingStateFlow(private val billingState : StateAndRef<BillingState>,
                              private val chipAmount : Long,
                              private val numberOfChips : Int = 1,
                              private val timeTolerance : Duration = TIME_TOLERANCE) : FlowLogic<Pair<List<BillingChipState>, SignedTransaction>>() {
    @Suspendable
    override fun call() : Pair<List<BillingChipState>, SignedTransaction> {
        val configuration = serviceHub.cordaService(MemberConfigurationService::class.java)
        val notary = configuration.notaryParty()
        val builder = TransactionBuilder(notary)
                .addInputState(billingState)
                .addCommand(BillingContract.Commands.ChipOff(), billingState.state.data.owner.owningKey)

        // if expiry date is present - adding a time window
        if (billingState.state.data.expiryDate != null) {
            builder.setTimeWindow(Instant.now(), timeTolerance)
        }

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
 * Chips off the rest of the unspent amount from the [billingState].
 * Example: if a [BillingState]'s unspent amount is 5 and [chipAmount] is 2 then the flow would chip off 2x2 [BillingChipState]s and leave 1 unspent.
 * The flow is applicable only to bounded billing states (i.e. with the positive issued amount)
 *
 * @param billingState the [BillingState] to chip off from
 * @param chipAmount the amount of each of the [BillingChipState]s
 * @param timeTolerance the time tolerance to be used for the transaction time window if the [BillingState] has an expiry date
 * @return a list of chipped off states with the associated [SignedTransaction]
 */
@StartableByRPC
class ChipOffRemainingAmountFlow(private val billingState : StateAndRef<BillingState>,
                                 private val chipAmount : Long,
                                 private val timeTolerance : Duration = TIME_TOLERANCE) : FlowLogic<Pair<List<BillingChipState>, SignedTransaction>?>() {
    @Suspendable
    override fun call() : Pair<List<BillingChipState>, SignedTransaction>? {
        if (billingState.state.data.issued == 0L) {
            throw FlowException("Issued amount should be positive")
        }
        val numberOfChips = (billingState.state.data.issued - billingState.state.data.spent) / chipAmount
        if (numberOfChips <= 0) {
            return null
        }
        return subFlow(ChipOffBillingStateFlow(billingState, chipAmount, numberOfChips.toInt(), timeTolerance))

    }
}