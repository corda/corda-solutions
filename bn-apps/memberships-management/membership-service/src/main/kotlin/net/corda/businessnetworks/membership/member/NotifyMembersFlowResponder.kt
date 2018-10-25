package net.corda.businessnetworks.membership.member

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.bno.NotifyMemberFlow
import net.corda.businessnetworks.membership.bno.OnMembershipActivated
import net.corda.businessnetworks.membership.bno.OnMembershipChanged
import net.corda.businessnetworks.membership.bno.OnMembershipRevoked
import net.corda.businessnetworks.membership.member.service.MemberConfigurationService
import net.corda.businessnetworks.membership.member.service.MembershipsCacheHolder
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.utilities.unwrap

/**
 * Responder to the [NotifyMemberFlow]. The flow updates memberships cache according to a notification from BNO
 */
@InitiatedBy(NotifyMemberFlow::class)
class NotifyMembersFlowResponder(private val session : FlowSession) : FlowLogic<Unit>(){

    @Suspendable
    override fun call() {
        val configuration = serviceHub.cordaService(MemberConfigurationService::class.java)
        if (configuration.bnoParty() != session.counterparty) {
            throw FlowException("Only BNO can start OnMembershipRevokedFlow")
        }

        val membershipService = serviceHub.cordaService(MembershipsCacheHolder::class.java)
        val notification = session.receive<Any>().unwrap { it }
        val cache = membershipService.cache
        if (cache != null) {
            when (notification) {
            // removes revoked memberships from the cache
                is OnMembershipRevoked -> {
                    if (notification.revokedMember != ourIdentity) {
                        cache.revokeMembership(notification.revokedMember)
                    } else {
                        membershipService.resetCache()
                    }
                }
            // updates membership in the cache
                is OnMembershipChanged -> {
                    if (notification.changedMembership.state.data.member != ourIdentity) {
                        cache.updateMembership(notification.changedMembership)
                    }
                }
                is OnMembershipActivated -> {
                    // refreshing membership list if its our identity or applying change to the cache otherwise
                    if (notification.changedMembership.state.data.member == ourIdentity) {
                        subFlow(GetMembershipsFlow(forceRefresh = true))
                    } else {
                        cache.updateMembership(notification.changedMembership)
                    }
                }
                else -> throw IllegalArgumentException("Unknown notification $notification")
            }
        }
    }
}