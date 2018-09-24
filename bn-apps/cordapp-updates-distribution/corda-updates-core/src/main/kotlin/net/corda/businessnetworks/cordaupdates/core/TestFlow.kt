package net.corda.businessnetworks.cordaupdates.core

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC

@StartableByRPC
class TestFlow(private val remoteRepoUrl : String, private val localRepoPath : String, val mavenCoords : String) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        logger.info("Starting TestFlow")

        val resolver = CordaMavenResolver(remoteRepoUrl, localRepoPath)
        resolver.downloadVersion(mavenCoords)
    }
}