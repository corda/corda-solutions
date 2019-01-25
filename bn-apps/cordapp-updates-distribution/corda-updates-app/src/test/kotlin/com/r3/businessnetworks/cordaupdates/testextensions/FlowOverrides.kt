package com.r3.businessnetworks.cordaupdates.testextensions

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.cordaupdates.app.bno.ReportCordappVersionFlowResponder
import com.r3.businessnetworks.cordaupdates.app.member.ReportCordappVersionFlow
import com.r3.businessnetworks.cordaupdates.transport.flows.GetResourceFlow
import com.r3.businessnetworks.cordaupdates.transport.flows.GetResourceFlowResponder
import com.r3.businessnetworks.cordaupdates.transport.flows.PeekResourceFlow
import com.r3.businessnetworks.cordaupdates.transport.flows.PeekResourceFlowResponder
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy

@InitiatedBy(ReportCordappVersionFlow::class)
class ReportCordappVerionFlowResponderWithSessionFilter(session : FlowSession) : ReportCordappVersionFlowResponder(session) {
    @Suspendable
    override fun isSessionAllowed(session : FlowSession) = false
}