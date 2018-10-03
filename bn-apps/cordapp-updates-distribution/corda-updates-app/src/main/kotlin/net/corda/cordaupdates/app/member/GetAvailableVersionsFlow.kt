package net.corda.cordaupdates.app.member

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.cordaupdates.core.ArtifactMetadata
import net.corda.businessnetworks.cordaupdates.core.SyncerConfiguration
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC

@StartableByRPC
class GetAvailableVersionsFlow(private val syncerConfig : SyncerConfiguration? = null, private val launchAsync : Boolean = true) : FlowLogic<List<ArtifactMetadata>?>() {
    @Suspendable
    override fun call() : List<ArtifactMetadata>? {
        val syncerService = serviceHub.cordaService(SyncerService::class.java)
        return if (launchAsync) {
            syncerService.getArtifactsMetadataAsync(syncerConfig)
            null
        } else {
            syncerService.getArtifactsMetadata(syncerConfig)
        }
    }
}

