package net.corda.businessnetworks.membership.bno.extension

import net.corda.businessnetworks.membership.states.MembershipState

/**
 * Class that, if defined in configuration, is invoked by a BNO against incoming membership requests. If returns true - then the membership would be
 * activated automatically. The main intention - is to provide an API extension points for BNO for automatic membership verification.
 *
 * TODO: remove MembershipAutoAcceptor in favour of flow overrides when Corda 4 is released
 */
interface MembershipAutoAcceptor {
    fun autoActivateThisMembership(membershipState : MembershipState<Any>) : Boolean
}