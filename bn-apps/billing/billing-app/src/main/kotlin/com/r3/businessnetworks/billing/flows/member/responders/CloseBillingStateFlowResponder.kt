package com.r3.businessnetworks.billing.flows.member.responders

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.billing.flows.bno.CloseBillingStateFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.ReceiveFinalityFlow

@InitiatedBy(CloseBillingStateFlow::class)
class CloseBillingStateFlowResponder(private val session : FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        //nothing to verify here
        subFlow(ReceiveFinalityFlow(session))
    }
}