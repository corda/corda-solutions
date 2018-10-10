package net.corda.cordaupdates.app

import co.paralleluniverse.fibers.Suspendable
import net.corda.cordaupdates.app.member.MemberConfiguration
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import java.io.File

@StartableByRPC
class ReloadConfigurationFlow(val fileName : String) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val config = File(ReloadConfigurationFlow::class.java.classLoader.getResource(fileName).toURI())
        val configuration = serviceHub.cordaService(MemberConfiguration::class.java)
        configuration.reloadConfigurationFromFile(config)
    }
}

