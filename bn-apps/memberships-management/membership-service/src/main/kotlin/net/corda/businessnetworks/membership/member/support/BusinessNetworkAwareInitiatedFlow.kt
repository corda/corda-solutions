package net.corda.businessnetworks.membership.member.support

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.NotAMemberException
import net.corda.businessnetworks.membership.member.GetMembershipsFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.Party

/**
 * Extend from this class if you are a business network member and you don't want to be checking yourself whether
 * the initiating party is also a member. Your code (inside onCounterpartyMembershipVerified) will be called only after
 * that check is performed. If the initiating party is not a member an exception is thrown.
 */
abstract class BusinessNetworkAwareInitiatedFlow<out T>(protected val session: FlowSession) : FlowLogic<T>() {
    @Suspendable
    override fun call(): T {
        verifyMembership(session.counterparty)
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
    private fun verifyMembership(initiator : Party) {
        val membership = subFlow(GetMembershipsFlow(bnoIdentity()))[initiator]
        // inactive members can't transact on the network
        if(membership == null || !membership.state.data.isActive()) {
            throw NotAMemberException(initiator)
        }
    }
}

