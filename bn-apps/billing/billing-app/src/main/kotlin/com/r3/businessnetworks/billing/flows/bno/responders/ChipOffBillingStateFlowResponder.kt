package com.r3.businessnetworks.billing.flows.bno.responders

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.billing.flows.member.ChipOffBillingStateFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.ReceiveFinalityFlow

@InitiatedBy(ChipOffBillingStateFlow::class)
class ChipOffBillingStateFlowResponder(private val session : FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // we have nothing to check here
        subFlow(ReceiveFinalityFlow(session))
    }
}