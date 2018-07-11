package net.corda.businessnetworks.membership.bno

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.bno.BNOUtils.verifyMembership
import net.corda.businessnetworks.membership.bno.service.BNOConfigurationService
import net.corda.businessnetworks.membership.bno.service.DatabaseService
import net.corda.businessnetworks.membership.member.GetMembershipsFlow
import net.corda.businessnetworks.membership.member.MembershipListRequest
import net.corda.businessnetworks.membership.member.MembershipsListResponse
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy

/**
 * Responder to the [GetMembershipsFlow].
 */
@InitiatedBy(GetMembershipsFlow::class)
class GetMembershipListFlowResponder(private val session : FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // verify that our counterpart is a valid member
        verifyMembership(serviceHub, session.counterparty)

        //build memberships snapshot
        session.receive<MembershipListRequest>()
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        val memberships = databaseService.getActiveMemberships()
        val configurationService  = serviceHub.cordaService(BNOConfigurationService::class.java)
        val cacheRefreshPeriod = configurationService.cacheRefreshPeriod()

        val nextRefreshTime = if (cacheRefreshPeriod == null) null
            else serviceHub.clock.instant().plusSeconds(cacheRefreshPeriod.toLong() * 60 * 60)

        session.send(MembershipsListResponse(memberships, nextRefreshTime))
    }
}
