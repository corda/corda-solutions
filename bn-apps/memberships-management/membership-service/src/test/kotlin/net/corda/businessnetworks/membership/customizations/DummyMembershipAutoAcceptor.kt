package net.corda.businessnetworks.membership.customizations

import net.corda.businessnetworks.membership.bno.extension.MembershipAutoAcceptor
import net.corda.businessnetworks.membership.states.MembershipState

class DummyMembershipAutoAcceptor : MembershipAutoAcceptor {

    override fun autoActivateThisMembership(membershipState: MembershipState<Any>): Boolean {
        return true
    }

}