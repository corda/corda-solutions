package net.corda.cordaupdates.app.member

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.serialization.CordaSerializable

/**
 * Represents a single version of a cordapp.
 *
 * @param group cordapp group, i.e. "net.corda"
 * @param name cordapp name, i.e. "corda-finance"
 * @param version cordapp version, i.e. "1.0"
 * @param updated last updated timestamp
 */
@CordaSerializable
data class CordappVersionInfo(val group : String, val name : String, val version : String, val updated : Long = 0L)

/**
 * Reports a version of cordapp to the BNO
 */
@InitiatingFlow
class ReportCordappVersionFlow(private val group : String, private val name : String, private val version : String) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val configuration = serviceHub.cordaService(MemberConfiguration::class.java)
        val bno = configuration.bnoParty()
        val session = initiateFlow(bno)
        session.send(CordappVersionInfo(group, name, version))
    }
}