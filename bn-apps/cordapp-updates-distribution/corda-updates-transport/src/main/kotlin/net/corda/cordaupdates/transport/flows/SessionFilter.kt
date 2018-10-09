package net.corda.cordaupdates.transport.flows

import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession

/**
 * This interface can be implemented by third-party applications to prevent unauthorised peers from accessing their repositories
 */
interface SessionFilter {
    fun isSessionAllowed(session : FlowSession, flowLogic : FlowLogic<*>) : Boolean
}