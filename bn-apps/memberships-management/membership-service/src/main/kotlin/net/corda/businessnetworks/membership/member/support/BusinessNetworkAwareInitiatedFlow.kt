package net.corda.businessnetworks.membership.member.support

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.common.CounterPartyNotAMemberException
import net.corda.businessnetworks.membership.member.GetMembershipsFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.Party

abstract class BusinessNetworkAwareInitiatedFlow<out T>(val flowSession: FlowSession) : FlowLogic<T>() {

    @Suspendable
    override fun call(): T {
        confirmInitiatorIsAMemberOfThisBN(flowSession.counterparty)
        return onOtherPartyMembershipVerified()
    }

    @Suspendable
    abstract fun onOtherPartyMembershipVerified() : T

    @Suspendable
    private fun confirmInitiatorIsAMemberOfThisBN(initiator : Party) {
        val memberships = subFlow(GetMembershipsFlow())
        if(memberships[initiator] == null) {
            throw CounterPartyNotAMemberException(initiator)
        }
    }
}

