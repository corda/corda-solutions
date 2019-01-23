package com.r3.businessnetworks.membership.flows.customizations

import com.r3.businessnetworks.membership.flows.bno.extension.MembershipAutoAcceptor
import com.r3.businessnetworks.membership.states.MembershipState

class DummyMembershipAutoAcceptor : MembershipAutoAcceptor {

    override fun autoActivateThisMembership(membershipState: MembershipState<Any>): Boolean {
        return true
    }

}