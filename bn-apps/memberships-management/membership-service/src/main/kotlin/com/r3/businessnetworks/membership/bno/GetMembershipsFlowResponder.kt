package com.r3.businessnetworks.membership.bno

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.bno.service.BNOConfigurationService
import com.r3.businessnetworks.membership.bno.service.DatabaseService
import com.r3.businessnetworks.membership.bno.support.BusinessNetworkOperatorInitiatedFlow
import com.r3.businessnetworks.membership.member.GetMembershipsFlow
import com.r3.businessnetworks.membership.member.MembershipListRequest
import com.r3.businessnetworks.membership.member.MembershipsListResponse
import com.r3.businessnetworks.membership.states.MembershipState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy

/**
 * Responder to the [GetMembershipsFlow]. Only active members can request a membership list.
 */
@InitiatedBy(GetMembershipsFlow::class)
class GetMembershipsFlowResponder(flowSession : FlowSession) : BusinessNetworkOperatorInitiatedFlow<Unit>(flowSession) {
    @Suspendable
    override fun onCounterpartyMembershipVerified(counterpartyMembership : StateAndRef<MembershipState<Any>>) {
        logger.info("Sending membership list to ${flowSession.counterparty}")
        //build memberships snapshot
        flowSession.receive<MembershipListRequest>()
        val configuration = serviceHub.cordaService(BNOConfigurationService::class.java)
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        val membershipsWhereWeAreBNO = databaseService.getAllMemberships(ourIdentity, configuration.membershipContractName())
        flowSession.send(MembershipsListResponse(membershipsWhereWeAreBNO))
    }
}