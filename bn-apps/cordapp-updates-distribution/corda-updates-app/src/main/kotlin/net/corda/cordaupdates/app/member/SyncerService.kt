package net.corda.cordaupdates.app.member

import net.corda.businessnetworks.cordaupdates.core.ArtifactMetadata
import net.corda.businessnetworks.cordaupdates.core.CordappSyncer
import net.corda.businessnetworks.cordaupdates.core.SyncerConfiguration
import net.corda.cordaupdates.transport.ConfigurationProperties
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@CordaService
class ArtifactsMetadataCache(private val appServiceHub : AppServiceHub) : SingletonSerializeAsToken() {
    private var _artifactsCache : List<ArtifactMetadata> = listOf()
    var artifactsCache : List<ArtifactMetadata>
        internal set(value) { _artifactsCache = value }
        get() = _artifactsCache
}

@CordaService
internal class SyncerService(private val appServiceHub : AppServiceHub) : SingletonSerializeAsToken() {
    companion object {
        val executor = Executors.newSingleThreadExecutor()!!
    }

    private fun getConfigurationFileFromDefaultLocations() : File? {
        val localConfig = File("settings.yaml")
        if (localConfig.exists()) return localConfig
        val userConfig = File("${System.getProperty("user.home")}/.corda-updates/settings.yaml")
        return if (userConfig.exists()) userConfig else null
    }


    private fun syncer(syncerConfiguration : SyncerConfiguration? = null) =
        if (syncerConfiguration == null) {
            val configuration = appServiceHub.cordaService(MemberConfiguration::class.java)
            val syncerConfigPath = configuration.syncerConfig()
            val config = (if (syncerConfigPath == null) getConfigurationFileFromDefaultLocations() else File(syncerConfigPath))
                    ?: throw CordaUpdatesException("Configuration for CordappSyncer has not been found")
            CordappSyncer(SyncerConfiguration.readFromFile(config))
        } else CordappSyncer(syncerConfiguration)

    private fun additionalConfigurationProperties() = mapOf(Pair(ConfigurationProperties.APP_SERVICE_HUB, appServiceHub))

    fun syncArtifacts(syncerConfiguration : SyncerConfiguration? = null) : List<ArtifactMetadata> {
        val syncer = syncer(syncerConfiguration)
        val artifacts = syncer.syncCordapps(additionalConfigurationProperties())
        val artifactsMetadataCache = appServiceHub.cordaService(ArtifactsMetadataCache::class.java)
        artifactsMetadataCache.artifactsCache = artifacts
        return artifacts
    }

    fun getArtifactsMetadata(syncerConfiguration : SyncerConfiguration? = null) : List<ArtifactMetadata>  {
        val syncer = syncer(syncerConfiguration)
        val artifacts = syncer.getAvailableVersions(additionalConfigurationProperties())
        val artifactsMetadataCache = appServiceHub.cordaService(ArtifactsMetadataCache::class.java)
        artifactsMetadataCache.artifactsCache = artifacts
        return artifacts
    }

    fun syncArtifactsAsync(syncerConfiguration : SyncerConfiguration? = null) {
        executor.submit(Callable { syncArtifacts(syncerConfiguration) })
    }

    fun getArtifactsMetadataAsync(syncerConfiguration : SyncerConfiguration? = null) {
        executor.submit(Callable { getArtifactsMetadata(syncerConfiguration) })
    }
}

class CordaUpdatesException(message : String) : Exception(message)
