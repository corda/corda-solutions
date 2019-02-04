package com.r3.businessnetworks.billing.flows.bno

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.billing.states.BillingContract
import com.r3.businessnetworks.billing.states.BillingState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

@StartableByRPC
@InitiatingFlow
class RetireBillingStateFlow(private val billingState : StateAndRef<BillingState>) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call() : SignedTransaction {
        val notary = serviceHub.networkMapCache.notaryIdentities.single()
        val builder = TransactionBuilder(notary)
                .addInputState(billingState)
                .addCommand(BillingContract.Commands.Return(), ourIdentity.owningKey)

        builder.verify(serviceHub)

        val stx = serviceHub.signInitialTransaction(builder)

        val session = initiateFlow(billingState.state.data.owner)

        return subFlow(FinalityFlow(stx, listOf(session)))
    }
}

@StartableByRPC
class RetireBillingStateForPartyFlow(private val party : Party) : FlowLogic<List<SignedTransaction>>() {
    @Suspendable
    override fun call() : List<SignedTransaction> {
        return serviceHub.vaultService.queryBy<BillingState>().states.filter { it.state.data.owner == party }
                .map { subFlow(RetireBillingStateFlow(it)) }.toList()
    }
}