package com.r3.businessnetworks.cordaupdates.app.member

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.cordaupdates.core.ArtifactMetadata
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.ProgressTracker
import java.time.Instant

@CordaSerializable
data class SyncedCordappsResponse(val cordapps : List<ArtifactMetadata>, val lastUpdated : Instant?)

@StartableByRPC
class GetSyncedCordappsFlow : FlowLogic<SyncedCordappsResponse>() {
    companion object {
        object GETTING_VERSIONS : ProgressTracker.Step("Getting versions")

        fun tracker() = ProgressTracker(
                GETTING_VERSIONS
        )
    }

    override val progressTracker : ProgressTracker = tracker()

    @Suspendable
    override fun call() : SyncedCordappsResponse {
        progressTracker.currentStep = GETTING_VERSIONS
        val cache = serviceHub.cordaService(ArtifactsMetadataCache::class.java)
        return SyncedCordappsResponse(cache.cache, cache.lastUpdated)
    }
}