package com.r3.businessnetworks.billing.flows.bno

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.billing.states.BillingContract
import com.r3.businessnetworks.billing.states.BillingState
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

@InitiatingFlow
class IssueBillingStateFlow(private val owner : Party,
                            private val amount : Long) : FlowLogic<Pair<BillingState, SignedTransaction>>() {
    @Suspendable
    override fun call() : Pair<BillingState, SignedTransaction> {
        val notary = serviceHub.networkMapCache.notaryIdentities.single()
        val billingState = BillingState(ourIdentity, owner, amount, 0L)
        val builder = TransactionBuilder(notary)
                .addOutputState(billingState, BillingContract.CONTRACT_NAME)
                .addCommand(BillingContract.Commands.Issue(), ourIdentity.owningKey, owner.owningKey)

        builder.verify(serviceHub)

        val stx = serviceHub.signInitialTransaction(builder)

        val session = initiateFlow(owner)

        val allSignedTx = subFlow(CollectSignaturesFlow(stx, listOf(session)))

        return Pair(billingState, subFlow(FinalityFlow(allSignedTx, listOf(session))))
    }
}