package net.corda.cordaupdates.transport.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession

/**
 * This interface can be implemented by third-party applications to prevent unauthorised peers from accessing their repositories
 */
interface SessionFilter {
    @Suspendable
    fun isSessionAllowed(session : FlowSession, flowLogic : FlowLogic<*>) : Boolean
}