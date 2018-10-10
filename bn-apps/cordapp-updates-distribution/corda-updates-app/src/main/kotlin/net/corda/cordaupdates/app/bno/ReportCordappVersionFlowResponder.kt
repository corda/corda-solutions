package net.corda.cordaupdates.app.bno

import co.paralleluniverse.fibers.Suspendable
import net.corda.cordaupdates.app.member.CordappVersionInfo
import net.corda.cordaupdates.app.member.ReportCordappVersionFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.ProgressTracker
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
    companion object {
        val QUERYING_FROM_DATABASE = ProgressTracker.Step("Querying data from the database")

        fun tracker() = ProgressTracker(QUERYING_FROM_DATABASE)
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call() : Map<String, List<CordappVersionInfo>> {
        progressTracker.currentStep = QUERYING_FROM_DATABASE
        val dbService = serviceHub.cordaService(DatabaseService::class.java)
        return dbService.getCordappVersionInfos()
    }
}

@StartableByRPC
class GetCordappVersionsForPartyFlow(private val partyName : String) : FlowLogic<List<CordappVersionInfo>>() {
    companion object {
        val QUERYING_FROM_DATABASE = ProgressTracker.Step("Querying data from the database")

        fun tracker() = ProgressTracker(QUERYING_FROM_DATABASE)
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call() : List<CordappVersionInfo> {
        progressTracker.currentStep = QUERYING_FROM_DATABASE
        val dbService = serviceHub.cordaService(DatabaseService::class.java)
        val party = serviceHub.identityService.wellKnownPartyFromX500Name(CordaX500Name.parse(partyName))!!
        return dbService.getCordappVersionInfos(party)
    }
}