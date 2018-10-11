package net.corda.cordaupdates.app.member

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.cordaupdates.core.ArtifactMetadata
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC

/**
 * Returns available CorDapp versions. The flow takes versions from the cache, that is populated by [SyncArtifactsFlow].
 * At least one round of synchronisation has to pass in order for the cache to be populated.
 */
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