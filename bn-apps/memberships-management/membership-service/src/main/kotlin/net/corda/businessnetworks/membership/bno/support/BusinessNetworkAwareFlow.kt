package net.corda.businessnetworks.membership.bno.support

import net.corda.businessnetworks.membership.common.NotBusinessOperatorOnThisMembership
import net.corda.businessnetworks.membership.states.Membership
import net.corda.core.flows.FlowLogic

abstract class BusinessNetworkAwareFlow<out T>() : FlowLogic<T>() {

    protected fun checkWeAreTheBNOOnThisMembership(membership : Membership.State) {
        if(ourIdentity != membership.bno) {
            throw NotBusinessOperatorOnThisMembership(membership)
        }
    }

}