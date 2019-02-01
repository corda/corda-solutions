package com.r3.businessnetworks.billing.flows.member

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.billing.flows.bno.RetireBillingStateFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.ReceiveFinalityFlow

@InitiatedBy(RetireBillingStateFlow::class)
class RetireBillingStateFlowResponder(val session : FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // nothing to verify here
        subFlow(ReceiveFinalityFlow(session))
    }
}