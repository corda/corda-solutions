package com.r3.businessnetworks.membership.flows.member

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.flows.bno.ActivateMembershipFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.ReceiveFinalityFlow

@InitiatedBy(ActivateMembershipFlow::class)
open class ActivateMembershipFlowResponder(private val session : FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        if (session.getCounterpartyFlowInfo().flowVersion == 1) {
            // do nothing
        } else {
            subFlow(ReceiveFinalityFlow(session))
        }
    }
}