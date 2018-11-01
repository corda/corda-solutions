package net.corda.cordaupdates.transport

import co.paralleluniverse.fibers.Suspendable
import net.corda.cordaupdates.transport.flows.RepositoryHosterConfigurationService
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import java.io.File

@StartableByRPC
class ReloadConfigurationFlow(private val fileName : String) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val config = File(ReloadConfigurationFlow::class.java.classLoader.getResource(fileName).toURI())
        val configuration = serviceHub.cordaService(RepositoryHosterConfigurationService::class.java)
        configuration.reloadConfigurationFromFile(config)
    }
}

