package net.corda.businessnetworks.membership.bno.support

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.bno.service.DatabaseService
import net.corda.businessnetworks.membership.MembershipNotActiveException
import net.corda.businessnetworks.membership.NotAMemberException
import net.corda.businessnetworks.membership.states.MembershipState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowSession
import net.corda.core.identity.Party

/**
 * Extend from this class if you are a business network operator and you don't want to be checking yourself whether
 * the initiating party is member of your business network or not. Your code (inside onCounterpartyMembershipVerified)
 * will be called only after the check is performed. If the initiating party is not a member an exception is thrown.
 */
abstract class BusinessNetworkOperatorInitiatedFlow<out T>(val flowSession : FlowSession) : BusinessNetworkOperatorFlowLogic<T>() {

    @Suspendable
    override fun call() : T {
        val membership = verifyAndGetMembership(flowSession.counterparty)
        return onCounterpartyMembershipVerified(membership)
    }

    @Suspendable
    abstract fun onCounterpartyMembershipVerified(counterpartyMembership : StateAndRef<MembershipState<Any>>) : T

    @Suspendable
    private fun verifyAndGetMembership(initiator : Party) : StateAndRef<MembershipState<Any>> {
        logger.info("Verifying membership status of $initiator")
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        val membership = databaseService.getMembership(initiator)
        if (membership == null) {
            throw NotAMemberException(initiator)
        } else if (!membership.state.data.isActive()) {
            throw MembershipNotActiveException(initiator)
        }
        return membership
    }
}

