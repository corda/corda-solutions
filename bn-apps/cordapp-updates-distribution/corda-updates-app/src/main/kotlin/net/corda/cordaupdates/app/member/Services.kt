package net.corda.cordaupdates.app.member

import net.corda.businessnetworks.cordaupdates.core.ArtifactMetadata
import net.corda.businessnetworks.cordaupdates.core.ArtifactsSyncer
import net.corda.businessnetworks.cordaupdates.core.SyncerConfiguration
import net.corda.cordaupdates.app.Utils
import net.corda.cordaupdates.transport.flows.ConfigurationProperties
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@CordaService
class ArtifactMetadataHolder(private val appServiceHub : AppServiceHub) : SingletonSerializeAsToken() {
    private var _artifacts : List<ArtifactMetadata> = listOf()
    var artifacts : List<ArtifactMetadata>
        internal set(value) { _artifacts = value }
        get() = _artifacts
}

@CordaService
class SyncerService(private val appServiceHub : AppServiceHub) : SingletonSerializeAsToken() {
    companion object {
        val executor = Executors.newSingleThreadExecutor()!!
    }

    private fun syncer(syncerConfiguration : SyncerConfiguration? = null) = ArtifactsSyncer(syncerConfiguration ?: Utils.syncerConfiguration(appServiceHub))
    private fun extraConfig() = mapOf(Pair(ConfigurationProperties.APP_SERVICE_HUB, appServiceHub))


    fun syncArtifacts(syncerConfiguration : SyncerConfiguration? = null) : List<ArtifactMetadata> {
        val syncer = syncer(syncerConfiguration)
        val artifacts = syncer.syncArtifacts(extraConfig())
        val artifactMetadataHolder = appServiceHub.cordaService(ArtifactMetadataHolder::class.java)
        artifactMetadataHolder.artifacts = artifacts
        return artifacts
    }

    fun getArtifactsMetadata(syncerConfiguration : SyncerConfiguration? = null) : List<ArtifactMetadata>  {
        val syncer = syncer(syncerConfiguration)
        val artifacts = syncer.getAvailableVersions(extraConfig())
        val artifactMetadataHolder = appServiceHub.cordaService(ArtifactMetadataHolder::class.java)
        artifactMetadataHolder.artifacts = artifacts
        return artifacts
    }

    fun syncArtifactsAsync(syncerConfiguration : SyncerConfiguration? = null) {
        executor.submit(Callable { syncArtifacts(syncerConfiguration) })
    }

    fun getArtifactsMetadataAsync(syncerConfiguration : SyncerConfiguration? = null) {
        executor.submit(Callable { getArtifactsMetadata(syncerConfiguration) })
    }
}
