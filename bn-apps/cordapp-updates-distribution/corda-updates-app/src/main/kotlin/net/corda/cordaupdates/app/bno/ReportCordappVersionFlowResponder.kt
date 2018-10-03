package net.corda.cordaupdates.app.bno

import co.paralleluniverse.fibers.Suspendable
import net.corda.cordaupdates.app.member.CordappVersionInfo
import net.corda.cordaupdates.app.member.ReportCordappVersionFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.utilities.unwrap

@InitiatedBy(ReportCordappVersionFlow::class)
class ReportCordappVersionFlowResponder(private val session : FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        session.receive<CordappVersionInfo>().unwrap { it }
    }
}