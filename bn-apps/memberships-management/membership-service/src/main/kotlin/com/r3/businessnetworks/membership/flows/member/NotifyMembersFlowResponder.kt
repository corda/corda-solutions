package com.r3.businessnetworks.membership.flows.member

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.flows.bno.NotifyMemberFlow
import com.r3.businessnetworks.membership.flows.bno.OnMembershipChanged
import com.r3.businessnetworks.membership.flows.member.Utils.throwExceptionIfNotBNO
import com.r3.businessnetworks.membership.flows.member.service.MembershipsCacheHolder
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.utilities.unwrap

/**
 * Responder to the [NotifyMemberFlow]. The flow updates memberships cache with notifications from BNO
 */
@InitiatedBy(NotifyMemberFlow::class)
open class NotifyMembersFlowResponder(val session : FlowSession) : FlowLogic<Any>() {

    @Suspendable
    override fun call() : Any{
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
        return notification
    }
}