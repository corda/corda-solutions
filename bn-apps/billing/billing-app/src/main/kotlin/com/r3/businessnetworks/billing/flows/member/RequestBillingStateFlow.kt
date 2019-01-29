package com.r3.businessnetworks.billing.flows.member

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.billing.states.BillingState
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap

@InitiatingFlow
class RequestBillingStateFlow(val bno : Party,
                              val amount : Long) : FlowLogic<Pair<BillingState, SignedTransaction>>() {

    @Suspendable
    override fun call(): Pair<BillingState, SignedTransaction> {
        val session = initiateFlow(bno)
        return session.sendAndReceive<Pair<BillingState, SignedTransaction>>(amount).unwrap { it }
    }
}