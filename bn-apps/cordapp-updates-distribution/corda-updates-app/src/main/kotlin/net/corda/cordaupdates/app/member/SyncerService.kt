package net.corda.cordaupdates.app.member

import net.corda.businessnetworks.cordaupdates.core.ArtifactMetadata
import net.corda.businessnetworks.cordaupdates.core.CordappSyncer
import net.corda.businessnetworks.cordaupdates.core.SyncerConfiguration
import net.corda.cordaupdates.transport.SessionConfigurationProperties
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * [ArtifactMetadata] cache that is populated by [SyncArtifactsFlow] for consumption by other flows
 */
@CordaService
class ArtifactsMetadataCache(private val appServiceHub : AppServiceHub) : SingletonSerializeAsToken() {
    private var _cache : List<ArtifactMetadata> = listOf()
    var cache : List<ArtifactMetadata>
        internal set(value) { _cache = value }
        get() = _cache
}

/**
 * Wrapper around [CordappSyncer]. Supports both synchronous and asynchronous invocations.
 * Results of both synchronous and asynchronous invocations are cached to [ArtifactsMetadataCache]
 */
@CordaService
internal class SyncerService(private val appServiceHub : AppServiceHub) : SingletonSerializeAsToken() {
    companion object {
        val executor = Executors.newSingleThreadExecutor()!!
    }

    private fun getConfigurationFileFromDefaultLocations() : File? {
        val userConfig = File("${System.getProperty("user.home")}/.corda-updates/settings.conf")
        return if (userConfig.exists()) userConfig else null
    }

    /**
     * Configures [CordappSyncer]. [CordappSyncer] configuration is red from the path provided in the cordapp settings file and defaults
     * to ~/.corda-updates/settings.conf if not specified.
     */
    private fun syncer(syncerConfiguration : SyncerConfiguration? = null) =
        if (syncerConfiguration == null) {
            val configuration = appServiceHub.cordaService(MemberConfiguration::class.java)
            val syncerConfigPath = configuration.syncerConfig()
            val config = (if (syncerConfigPath == null) getConfigurationFileFromDefaultLocations() else File(syncerConfigPath))
                    ?: throw CordaUpdatesException("Configuration for CordappSyncer has not been found")
            CordappSyncer(SyncerConfiguration.readFromFile(config))
        } else CordappSyncer(syncerConfiguration)

    private fun additionalConfigurationProperties() = mapOf(Pair(SessionConfigurationProperties.APP_SERVICE_HUB, appServiceHub))

    /**
     * Synchronously launches artifact synchronisation.
     */
    fun syncArtifacts(syncerConfiguration : SyncerConfiguration? = null) : List<ArtifactMetadata> {
        val syncer = syncer(syncerConfiguration)
        val artifacts = syncer.syncCordapps(additionalConfigurationProperties = additionalConfigurationProperties())
        val artifactsMetadataCache = appServiceHub.cordaService(ArtifactsMetadataCache::class.java)
        artifactsMetadataCache.cache = artifacts
        return artifacts
    }

    /**
     * Synchronously gets artifacts metadata.
     *
     * @param cordappCoordinatesWithRange coordinates of cordapp with range, i.e. "net.corda:corda-finance:[0,2.0)"
     */
    fun getArtifactsMetadata(cordappCoordinatesWithRange : String, syncerConfiguration : SyncerConfiguration? = null) : List<ArtifactMetadata>  {
        val syncer = syncer(syncerConfiguration)
        val artifacts = syncer.getAvailableVersions(cordappCoordinatesWithRange, additionalConfigurationProperties())
        val artifactsMetadataCache = appServiceHub.cordaService(ArtifactsMetadataCache::class.java)
        artifactsMetadataCache.cache = artifacts
        return artifacts
    }

    /**
     * Asynchronously launches artifact synchronisation
     */
    fun syncArtifactsAsync(syncerConfiguration : SyncerConfiguration? = null) {
        executor.submit(Callable { syncArtifacts(syncerConfiguration) })
    }

    /**
     * Asynchronously gets artifact metadata
     *
     * @param cordappCoordinatesWithRange coordinates of cordapp with range, i.e. "net.corda:corda-finance:[0,2.0)"
     */
    fun getArtifactsMetadataAsync(cordappCoordinatesWithRange : String, syncerConfiguration : SyncerConfiguration? = null) {
        executor.submit(Callable { getArtifactsMetadata(cordappCoordinatesWithRange, syncerConfiguration) })
    }
}

class CordaUpdatesException(message : String) : Exception(message)