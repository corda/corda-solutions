package net.corda.businessnetworks.membership.bno

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.bno.service.DatabaseService
import net.corda.businessnetworks.membership.bno.support.BusinessNetworkOperatorInitiatedFlow
import net.corda.businessnetworks.membership.member.GetMembershipsFlow
import net.corda.businessnetworks.membership.member.MembershipListRequest
import net.corda.businessnetworks.membership.member.MembershipsListResponse
import net.corda.businessnetworks.membership.states.MembershipState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy

/**
 * Responder to the [GetMembershipsFlow]. Only active members can request a membership list.
 */
@InitiatedBy(GetMembershipsFlow::class)
class GetMembershipListFlowResponder(flowSession : FlowSession) : BusinessNetworkOperatorInitiatedFlow<Unit>(flowSession) {
    @Suspendable
    override fun onCounterpartyMembershipVerified(counterpartyMembership : StateAndRef<MembershipState<Any>>) {
        logger.info("Sending membership list to ${flowSession.counterparty}")
        //build memberships snapshot
        flowSession.receive<MembershipListRequest>()
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        val memberships = databaseService.getActiveMemberships()

        flowSession.send(MembershipsListResponse(memberships))
    }
}