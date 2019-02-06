package com.r3.businessnetworks.billing.flows.member.responders

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.billing.flows.bno.RevokeBillingStateFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.ReceiveFinalityFlow

@InitiatedBy(RevokeBillingStateFlow::class)
class RevokeBillingStateFlowResponder(val session : FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // nothing to check here
        subFlow(ReceiveFinalityFlow(session))
    }
}