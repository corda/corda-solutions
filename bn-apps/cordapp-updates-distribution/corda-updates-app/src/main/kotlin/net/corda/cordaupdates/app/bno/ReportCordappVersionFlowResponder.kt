package net.corda.cordaupdates.app.bno

import co.paralleluniverse.fibers.Suspendable
import net.corda.cordaupdates.app.member.CordappVersionInfo
import net.corda.cordaupdates.app.member.ReportCordappVersionFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.unwrap

@InitiatedBy(ReportCordappVersionFlow::class)
class ReportCordappVersionFlowResponder(private val session : FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val cordappVersionInfo = session.receive<CordappVersionInfo>().unwrap { it }
        val dbService = serviceHub.cordaService(DatabaseService::class.java)
        dbService.updateCordappVersionInfo(session.counterparty, cordappVersionInfo)
    }
}

@StartableByRPC
class GetCordappVersionsFlow : FlowLogic<Map<String, List<CordappVersionInfo>>>() {
    @Suspendable
    override fun call() : Map<String, List<CordappVersionInfo>> {
        val dbService = serviceHub.cordaService(DatabaseService::class.java)
        return dbService.getCordappVersionInfos()
    }
}

@StartableByRPC
class GetCordappVersionsFlowForParty(private val partyName : String) : FlowLogic<List<CordappVersionInfo>>() {
    @Suspendable
    override fun call() : List<CordappVersionInfo> {
        val dbService = serviceHub.cordaService(DatabaseService::class.java)
        val party = serviceHub.identityService.wellKnownPartyFromX500Name(CordaX500Name.parse(partyName))!!
        return dbService.getCordappVersionInfos(party)
    }
}