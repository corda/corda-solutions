package net.corda.businessnetworks.membership.member

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.bno.RevokeMembershipFlow
import net.corda.core.flows.*

@InitiatedBy(RevokeMembershipFlow::class)
class RevokeMembershipFlowResponder(val session : FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {

        //@todo check the ctpty is the bno
        subFlow(ReceiveFinalityFlow(session))

    }


}