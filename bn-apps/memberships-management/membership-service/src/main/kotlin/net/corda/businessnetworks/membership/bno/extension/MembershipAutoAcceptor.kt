package net.corda.businessnetworks.membership.bno.extension

import net.corda.businessnetworks.membership.states.MembershipState

interface MembershipAutoAcceptor {
    fun autoActivateThisMembership(membershipState : MembershipState<Any>) : Boolean
}