package com.r3.businessnetworks.cordaupdates.testextensions.allowfilter

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.cordaupdates.transport.flows.GetResourceFlow
import com.r3.businessnetworks.cordaupdates.transport.flows.GetResourceFlowResponder
import com.r3.businessnetworks.cordaupdates.transport.flows.PeekResourceFlow
import com.r3.businessnetworks.cordaupdates.transport.flows.PeekResourceFlowResponder
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy

@InitiatedBy(GetResourceFlow::class)
class GetResourceFlowResponderWithSessionFilter(session : FlowSession) : GetResourceFlowResponder(session) {
    @Suspendable
    override fun isSessionAllowed(session : FlowSession) = true
}

@InitiatedBy(PeekResourceFlow::class)
class PeekResourceFlowResponderWithSessionFilter(session : FlowSession) : PeekResourceFlowResponder(session) {
    @Suspendable
    override fun isSessionAllowed(session : FlowSession) = true
}