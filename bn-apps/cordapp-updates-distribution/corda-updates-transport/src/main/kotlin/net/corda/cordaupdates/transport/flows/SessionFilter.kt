package net.corda.cordaupdates.transport.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession

/**
 * This interface can be implemented by a third-party applications to prevent an unauthorised peers from accessing their repositories
 *
 * For example a session filter that allows only Business Network traffic in, can be implemented in the following way using
 * Business Network Membership Service (https://github.com/corda/corda-solutions/tree/master/bn-apps/memberships-management)
 *
 * class BusinessNetworkSessionFilter : SessionFilter {
 *     // GetMembershipsFlow is a part of Business Network Membership Service implementation
 *     @Suspendable
 *     override fun isSessionAllowed(session : FlowSession, flowLogic : FlowLogic<*>) = flowLogic.subFlow(GetMembershipsFlow())[session.counterparty] != null
 * }
 */
interface SessionFilter {
    @Suspendable
    fun isSessionAllowed(session : FlowSession, flowLogic : FlowLogic<*>) : Boolean
}