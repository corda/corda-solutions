package com.r3.businessnetworks.membership.flows.member

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.commons.SupportReceiveFinalityFlow
import com.r3.businessnetworks.membership.flows.bno.ActivateMembershipFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy

@InitiatedBy(ActivateMembershipFlow::class)
class ActivateMembershipFlowResponder(private val session : FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(SupportReceiveFinalityFlow(session))
    }
}