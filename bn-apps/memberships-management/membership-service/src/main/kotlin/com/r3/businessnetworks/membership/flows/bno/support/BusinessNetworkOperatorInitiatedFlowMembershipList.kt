package com.r3.businessnetworks.membership.flows.bno.support

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.flows.MembershipNotActiveException
import com.r3.businessnetworks.membership.flows.NotAMemberException
import com.r3.businessnetworks.membership.flows.bno.service.DatabaseService
import com.r3.businessnetworks.membership.flows.member.MembershipListRequest
import com.r3.businessnetworks.membership.states.MembershipState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowSession
import net.corda.core.identity.Party
import net.corda.core.utilities.unwrap

/**
 * Extend from this class if you are a business network operator and you don't want to be checking yourself whether
 * the initiating party is member of your business network or not. Your code (inside onCounterpartyMembershipVerified)
 * will be called only after the check is performed. If the initiating party is not a member an exception is thrown.
 */
abstract class BusinessNetworkOperatorInitiatedFlowMembershipList<out T>(val flowSession : FlowSession) : BusinessNetworkOperatorFlowLogic<T>() {

    @Suspendable
    override fun call() : T {
        val receivedNetworkID = flowSession.receive<MembershipListRequest>().unwrap{it}
        val membership = verifyAndGetMembership(flowSession.counterparty,receivedNetworkID.networkID)
        return onCounterpartyMembershipVerified(membership)
    }

    @Suspendable
    abstract fun onCounterpartyMembershipVerified(counterpartyMembership : StateAndRef<MembershipState<Any>>) : T

    @Suspendable
    private fun verifyAndGetMembership(initiator : Party, networkID: String?) : StateAndRef<MembershipState<Any>> {
        logger.info("Verifying membership status of $initiator")
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        val membership = databaseService.getMembershipOnNetwork(initiator, ourIdentity, networkID)
        if (membership == null) {
            throw NotAMemberException(initiator)
        } else if (!membership.state.data.isActive()) {
            throw MembershipNotActiveException(initiator)
        }
        return membership
    }
}