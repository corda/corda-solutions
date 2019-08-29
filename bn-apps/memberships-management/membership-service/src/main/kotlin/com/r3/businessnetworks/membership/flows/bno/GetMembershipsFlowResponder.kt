package com.r3.businessnetworks.membership.flows.bno

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.flows.bno.service.DatabaseService
import com.r3.businessnetworks.membership.flows.bno.support.BusinessNetworkOperatorInitiatedFlowMembershipList
import com.r3.businessnetworks.membership.flows.member.GetMembershipsFlow
import com.r3.businessnetworks.membership.flows.member.MembershipListRequest
import com.r3.businessnetworks.membership.flows.member.MembershipsListResponse
import com.r3.businessnetworks.membership.states.MembershipState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy


/**
 * Responder to the [GetMembershipsFlow]. Only active members can request a membership list.
 */
@InitiatedBy(GetMembershipsFlow::class)
open class GetMembershipsFlowResponder(flowSession : FlowSession) : BusinessNetworkOperatorInitiatedFlowMembershipList<Unit>(flowSession) {
    @Suspendable
    override fun onCounterpartyMembershipVerified(counterpartyMembership : StateAndRef<MembershipState<Any>>) {
        logger.info("Sending membership list to ${flowSession.counterparty}")
        //build memberships snapshot
        flowSession.receive<MembershipListRequest>()
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        val membershipsWhereWeAreBNO = databaseService.getAllMemberships(ourIdentity)
        flowSession.send(MembershipsListResponse(membershipsWhereWeAreBNO))
    }
}