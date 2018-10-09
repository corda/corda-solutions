package net.corda.cordaupdates.transport

import net.corda.cordaupdates.transport.flows.SessionFilter
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession

class DenyAllSessionFilter : SessionFilter {
    override fun isSessionAllowed(session : FlowSession, flowLogic : FlowLogic<*>) = false
}

class AllowAllSessionFilter : SessionFilter {
    override fun isSessionAllowed(session : FlowSession, flowLogic : FlowLogic<*>) = true
}