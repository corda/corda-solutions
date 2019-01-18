package com.r3.businessnetworks.cordaupdates.app

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.cordaupdates.app.member.MemberConfiguration
import com.r3.businessnetworks.cordaupdates.transport.flows.RepositoryHosterConfigurationService
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import java.io.File

@StartableByRPC
class ReloadMemberConfigurationFlow(private val fileName : String) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val config = File(ReloadMemberConfigurationFlow::class.java.classLoader.getResource(fileName).toURI())
        val configuration = serviceHub.cordaService(MemberConfiguration::class.java)
        configuration.reloadConfigurationFromFile(config)
    }
}

@StartableByRPC
class ReloadBNOConfigurationFlow(private val fileName : String) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val config = File(ReloadBNOConfigurationFlow::class.java.classLoader.getResource(fileName).toURI())
        val configuration = serviceHub.cordaService(RepositoryHosterConfigurationService::class.java)
        configuration.reloadConfigurationFromFile(config)
    }
}

