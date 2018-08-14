package net.corda.businessnetworks.membership.bno.support

import net.corda.businessnetworks.membership.common.MembershipNotFound
import net.corda.businessnetworks.membership.common.MultipleMembershipsFound
import net.corda.businessnetworks.membership.common.NotBusinessOperatorOnThisMembership
import net.corda.businessnetworks.membership.states.Membership
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy

abstract class BusinessNetworkAwareFlow<out T>() : FlowLogic<T>() {

    protected fun verifyThatWeAreBNO(membership : Membership.State) {
        if(ourIdentity != membership.bno) {
            throw NotBusinessOperatorOnThisMembership(membership)
        }
    }

    protected fun findMembershipStateForParty(party : Party) : StateAndRef<Membership.State> {
        //@todo this could be made more effective and look for the Party's state in the vault
        val memberships = serviceHub.vaultService.queryBy<Membership.State>().states.filter { it.state.data.member == party }
        return when {
            memberships.isEmpty() -> throw MembershipNotFound(party)
            memberships.size == 1 -> memberships.first()
            else -> throw MultipleMembershipsFound(party)
        }
    }

}