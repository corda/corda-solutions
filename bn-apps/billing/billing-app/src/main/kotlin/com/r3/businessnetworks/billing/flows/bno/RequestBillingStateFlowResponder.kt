package com.r3.businessnetworks.billing.flows.bno

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.billing.flows.member.RequestBillingStateFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.utilities.unwrap

@InitiatedBy(RequestBillingStateFlow::class)
open class RequestBillingStateFlowResponder(private val session : FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val amount = session.receive<Long>().unwrap{
            verifyEligibility(it)
            it
        }
        val billingStateAndTransaction = subFlow(IssueBillingStateFlow(session.counterparty, amount))
        session.send(billingStateAndTransaction)
    }

    protected open fun verifyEligibility(amount : Long) = true
}