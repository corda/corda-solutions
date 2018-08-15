package net.corda.businessnetworks.membership.bno

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.bno.service.BNOConfigurationService
import net.corda.businessnetworks.membership.bno.service.DatabaseService
import net.corda.businessnetworks.membership.bno.support.BusinessNetworkOperatorInitiatedFlow
import net.corda.businessnetworks.membership.member.GetMembershipsFlow
import net.corda.businessnetworks.membership.member.MembershipListRequest
import net.corda.businessnetworks.membership.member.MembershipsListResponse
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy

/**
 * Responder to the [GetMembershipsFlow].
 */
@InitiatedBy(GetMembershipsFlow::class)
class GetMembershipListFlowResponder(flowSession : FlowSession) : BusinessNetworkOperatorInitiatedFlow<Unit>(flowSession) {

    @Suspendable
    override fun onOtherPartyMembershipVerified() {
        //build memberships snapshot
        flowSession.receive<MembershipListRequest>()
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        val memberships = databaseService.getActiveMemberships()
        val configurationService  = serviceHub.cordaService(BNOConfigurationService::class.java)
        val cacheRefreshPeriod = configurationService.cacheRefreshPeriod()

        val nextRefreshTime = if (cacheRefreshPeriod == null) null
            else serviceHub.clock.instant().plusSeconds(cacheRefreshPeriod.toLong() * 60 * 60)

        flowSession.send(MembershipsListResponse(memberships, nextRefreshTime))
    }

}
