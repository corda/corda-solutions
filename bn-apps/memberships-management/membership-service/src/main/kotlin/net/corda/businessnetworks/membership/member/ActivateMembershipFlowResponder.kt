package net.corda.businessnetworks.membership.member

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.bno.ActivateMembershipFlow
import net.corda.core.flows.*

@InitiatedBy(ActivateMembershipFlow::class)
class ActivateMembershipFlowResponder(val session : FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {

        //@todo check the ctpty is the bno
        subFlow(ReceiveFinalityFlow(session))

    }


}