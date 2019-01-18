package com.r3.businessnetworks.membership.customizations

import com.r3.businessnetworks.membership.bno.extension.MembershipAutoAcceptor
import com.r3.businessnetworks.membership.states.MembershipState

class DummyMembershipAutoAcceptor : MembershipAutoAcceptor {

    override fun autoActivateThisMembership(membershipState: MembershipState<Any>): Boolean {
        return true
    }

}