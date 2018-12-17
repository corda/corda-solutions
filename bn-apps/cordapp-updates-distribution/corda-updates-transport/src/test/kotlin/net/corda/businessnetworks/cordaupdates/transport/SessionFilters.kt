package net.corda.businessnetworks.cordaupdates.transport

import net.corda.businessnetworks.cordaupdates.transport.flows.SessionFilter
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession

class DenyAllSessionFilter : SessionFilter {
    override fun isSessionAllowed(session : FlowSession, flowLogic : FlowLogic<*>) = false
}

class AllowAllSessionFilter : SessionFilter {
    override fun isSessionAllowed(session : FlowSession, flowLogic : FlowLogic<*>) = true
}