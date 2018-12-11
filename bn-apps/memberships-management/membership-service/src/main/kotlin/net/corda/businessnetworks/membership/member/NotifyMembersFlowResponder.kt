package net.corda.businessnetworks.membership.member

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.bno.NotifyMemberFlow
import net.corda.businessnetworks.membership.bno.OnMembershipChanged
import net.corda.businessnetworks.membership.member.Utils.throwExceptionIfNotBNO
import net.corda.businessnetworks.membership.member.service.MembershipsCacheHolder
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.utilities.unwrap

/**
 * Responder to the [NotifyMemberFlow]. The flow updates memberships cache with notifications from BNO
 */
@InitiatedBy(NotifyMemberFlow::class)
class NotifyMembersFlowResponder(private val session : FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val bno = session.counterparty

        // don't forget to make sure that the messages are actually coming from the accepted BNOs
        throwExceptionIfNotBNO(bno, serviceHub)

        val membershipService = serviceHub.cordaService(MembershipsCacheHolder::class.java)
        val notification = session.receive<Any>().unwrap { it }
        val cache = membershipService.cache
        when (notification) {
            is OnMembershipChanged -> {
                val membership = notification.changedMembership.state.data
                // if our membership was suspended - then cleaning up the cache as suspended members won't get notifications anymore
                if (membership.member == ourIdentity && membership.isSuspended()) {
                    cache.reset(membership.bno)
                } else {
                    cache.updateMembership(notification.changedMembership)
                }
            }
            else -> throw IllegalArgumentException("Unknown notification $notification")
        }
    }

}