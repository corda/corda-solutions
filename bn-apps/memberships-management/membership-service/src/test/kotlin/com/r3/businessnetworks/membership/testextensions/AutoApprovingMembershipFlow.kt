package com.r3.businessnetworks.membership.testextensions

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.flows.bno.RequestMembershipFlowResponder
import com.r3.businessnetworks.membership.flows.bno.service.BNOConfigurationService
import com.r3.businessnetworks.membership.flows.member.RequestMembershipFlow
import com.r3.businessnetworks.membership.states.MembershipState
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy

@InitiatedBy(RequestMembershipFlow::class)
class AutoApprovingMembershipFlow(session : FlowSession) : RequestMembershipFlowResponder(session) {

    @Suspendable
    override fun activateRightAway(membershipState : MembershipState<Any>, configuration : BNOConfigurationService) : Boolean {
        return true
    }
}
