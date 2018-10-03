package net.corda.cordaupdates.app.member

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class CordappVersionInfo(val group : String, val name : String, val version : String)

@InitiatingFlow
class ReportCordappVersionFlow(val group : String, val name : String, val version : String) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val configuration = serviceHub.cordaService(MemberConfiguration::class.java)
        val bno = configuration.bnoParty()
        val session = initiateFlow(bno)
        session.send(CordappVersionInfo(group, name, version))
    }
}