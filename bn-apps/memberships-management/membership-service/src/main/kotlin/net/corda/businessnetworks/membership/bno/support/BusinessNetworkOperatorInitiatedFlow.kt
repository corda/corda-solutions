package net.corda.businessnetworks.membership.bno.support

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.bno.service.DatabaseService
import net.corda.businessnetworks.membership.common.MembershipNotActiveException
import net.corda.businessnetworks.membership.common.NotAMemberException
import net.corda.core.flows.FlowSession
import net.corda.core.identity.Party

/**
 * Extend from this class if you are a business network operator and you don't want to be checking yourself whether
 * the initiating party is member of your business network or not. Your code (inside onOtherPartyMembershipVerified)
 * will be called only after the check is performed. If the initiating party is not a member an exception is thrown.
 */
abstract class BusinessNetworkOperatorInitiatedFlow<out T>(val flowSession: FlowSession) : BusinessNetworkOperatorFlowLogic<T>() {

    @Suspendable
    override fun call(): T {
        verifyMembership(flowSession.counterparty)
        return onOtherPartyMembershipVerified()
    }

    @Suspendable
    abstract fun onOtherPartyMembershipVerified() : T

    @Suspendable
    private fun verifyMembership(initiator : Party) {
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        val membership = databaseService.getMembership(initiator)
        if (membership == null) {
            throw NotAMemberException(initiator)
        } else if (!membership.state.data.isActive()) {
            throw MembershipNotActiveException(initiator)
        }
    }
}

