package com.r3.businessnetworks.membership.bno

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.bno.support.BusinessNetworkOperatorFlowLogic
import com.r3.businessnetworks.membership.states.MembershipState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class OnMembershipChanged(val changedMembership : StateAndRef<MembershipState<Any>>)

/**
 * Flow that is used by BNO to notify active BN members about changes to the membership list.
 */
class NotifyActiveMembersFlow(private val notification : Any) : BusinessNetworkOperatorFlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val memberships = getActiveMembershipStates()
        memberships.forEach { subFlow(NotifyMemberFlow(notification, it.state.data.member)) }
    }
}

/**
 * Flow that is used by BNO to notify a BN member about changes to the membership list.
 */
@InitiatingFlow
class NotifyMemberFlow(private val notification : Any, private val member : Party) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        initiateFlow(member).send(notification)
    }
}



