package net.corda.businessnetworks.membership.customizations

import net.corda.businessnetworks.membership.bno.extension.MembershipAutoAcceptor
import net.corda.businessnetworks.membership.states.Membership

class DummyMembershipAutoAcceptor : MembershipAutoAcceptor {

    override fun autoActivateThisMembership(membershipState: Membership.State): Boolean {
        return true
    }

}