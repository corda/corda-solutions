package com.r3.businessnetworks.membership.flows.member.support

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.flows.NotAMemberException
import com.r3.businessnetworks.membership.flows.member.GetMembershipsFlow
import com.r3.businessnetworks.membership.flows.member.MembershipListRequest
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.identity.Party
import net.corda.core.utilities.unwrap

/**
 * Extend from this class if you are a business network member and you don't want to be checking yourself whether
 * the initiating party is also a member. Your code (inside onCounterpartyMembershipVerified) will be called only after
 * that check is performed. If the initiating party is not a member an exception is thrown.
 */

abstract class BusinessNetworkAwareInitiatedFlow<out T>(protected val flowSession : FlowSession, private val networkID: String?) : FlowLogic<T>() {
    @Suspendable
    override fun call(): T {
        verifyMembership(flowSession.counterparty, networkID)
        return onOtherPartyMembershipVerified()
    }

    /**
     * Will be called once counterpart's membership is successfully verified
     */
    @Suspendable
    abstract fun onOtherPartyMembershipVerified() : T

    /**
     * Identity of the BNO to verify counterpart's membership against
     */
    abstract fun bnoIdentity() : Party

    @Suspendable
    private fun verifyMembership(initiator : Party, networkID : String?) {
        // Memberships list contains valid active memberships only. So we need to just make sure that the membership exists.
        subFlow(GetMembershipsFlow(bnoIdentity(),networkID))[initiator] ?: throw NotAMemberException(initiator)
    }
}
