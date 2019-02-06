package com.r3.businessnetworks.billing.flows.bno

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.billing.flows.bno.service.BNOConfigurationService
import com.r3.businessnetworks.billing.states.BillingContract
import com.r3.businessnetworks.billing.states.BillingState
import com.r3.businessnetworks.billing.states.BillingStateStatus
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

/**
 * Closes [billingState]
 */
@StartableByRPC
@InitiatingFlow
class CloseBillingStateFlow(private val billingState : StateAndRef<BillingState>) : FlowLogic<Pair<BillingState, SignedTransaction>>() {

    @Suspendable
    override fun call() : Pair<BillingState, SignedTransaction> {
        if (billingState.state.data.issuer != ourIdentity) throw FlowException("Only state issuer can call this flow")

        val configuration = serviceHub.cordaService(BNOConfigurationService::class.java)
        val notary = configuration.notaryParty()
        val outputState = billingState.state.data.copy(status = BillingStateStatus.CLOSED)
        val builder = TransactionBuilder(notary)
                .addInputState(billingState)
                .addOutputState(outputState, BillingContract.CONTRACT_NAME)
                .addCommand(BillingContract.Commands.Close(), ourIdentity.owningKey)

        builder.verify(serviceHub)

        val stx = serviceHub.signInitialTransaction(builder)

        val session = initiateFlow(billingState.state.data.owner)

        return Pair(outputState, subFlow(FinalityFlow(stx, listOf(session))))
    }
}