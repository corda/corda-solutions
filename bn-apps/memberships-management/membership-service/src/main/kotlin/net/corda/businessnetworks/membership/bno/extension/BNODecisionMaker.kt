package net.corda.businessnetworks.membership.bno.extension

import net.corda.businessnetworks.membership.states.Membership

interface BNODecisionMaker {

    fun autoActivateThisMembership(membershipState : Membership.State) : Boolean

}