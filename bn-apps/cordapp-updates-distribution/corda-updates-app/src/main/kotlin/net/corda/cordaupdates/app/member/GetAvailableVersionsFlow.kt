package net.corda.cordaupdates.app.member

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.cordaupdates.core.ArtifactMetadata
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC

@StartableByRPC
class GetAvailableVersionsFlow(private val cordapp : String) : FlowLogic<ArtifactMetadata?>() {
    @Suspendable
    override fun call() : ArtifactMetadata? {
        val (group, artifact) = cordapp.split(":")
        val metadataCache = serviceHub.cordaService(ArtifactsMetadataCache::class.java)
        return metadataCache.cache.firstOrNull {
            it.group == group && it.name == artifact
        }
    }
}