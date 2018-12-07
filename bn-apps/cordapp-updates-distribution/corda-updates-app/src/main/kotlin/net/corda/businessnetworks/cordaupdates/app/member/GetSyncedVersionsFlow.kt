package net.corda.businessnetworks.cordaupdates.app.member

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.cordaupdates.core.ArtifactMetadata
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.utilities.ProgressTracker

@StartableByRPC
class GetSyncedVersionsFlow : FlowLogic<List<ArtifactMetadata>>() {
    companion object {
        object GETTING_VERSIONS : ProgressTracker.Step("Getting versions")

        fun tracker() = ProgressTracker(
                GETTING_VERSIONS
        )
    }

    override val progressTracker : ProgressTracker = tracker()

    @Suspendable
    override fun call() : List<ArtifactMetadata> {
        progressTracker.currentStep = GETTING_VERSIONS
        val cache = serviceHub.cordaService(ArtifactsMetadataCache::class.java)
        return cache.cache
    }
}