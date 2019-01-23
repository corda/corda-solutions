package com.r3.businessnetworks.membership.member

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.commons.SupportReceiveFinalityFlow
import com.r3.businessnetworks.membership.bno.SuspendMembershipFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy

@InitiatedBy(SuspendMembershipFlow::class)
class SuspendMembershipFlowResponder(val session : FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(SupportReceiveFinalityFlow(session))
    }
}