package net.corda.businessnetworks.cordaupdates.app.member

import net.corda.businessnetworks.cordaupdates.core.ArtifactMetadata
import net.corda.businessnetworks.cordaupdates.core.CordappSyncer
import net.corda.businessnetworks.cordaupdates.core.SyncerConfiguration
import net.corda.businessnetworks.cordaupdates.transport.APP_SERVICE_HUB
import net.corda.core.node.AppServiceHub
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.loggerFor
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * The cache that is populated by [SyncArtifactsFlow]. This cache is required to handle asynchronous invocations, when the results of the
 * flow execution are not available immediately and are populated to the cache in the future instead.
 *
 * The contents of the cache are updated regardless of whether [SyncArtifactsFlow] was invoked asynchronously or not.
 */
@CordaService
class ArtifactsMetadataCache(private val appServiceHub : AppServiceHub) : SingletonSerializeAsToken() {
    private var _cache : List<ArtifactMetadata> = listOf()
    var cache : List<ArtifactMetadata>
        internal set(value) {
            _cache = value
        }
        get() = _cache
}

/**
 * A service that wraps [CordappSyncer] to provide a support for asynchronous invocations.
 */
@CordaService
internal class SyncerService(private val appServiceHub : AppServiceHub) : SingletonSerializeAsToken() {

    companion object {
        val executor = Executors.newSingleThreadExecutor()!!
        val logger = loggerFor<SyncerService>()

        /**
         * Loads Synced configuration based on CorDapp config
         */
        fun loadSyncerConfiguration(serviceHub : ServiceHub) : SyncerConfiguration {
            val configuration : MemberConfiguration = serviceHub.cordaService(MemberConfiguration::class.java)
            val syncerConfigPath : String? = configuration.syncerConfig()
            return if (syncerConfigPath == null) {
                SyncerConfiguration.readFromConfig(configuration.config)
            } else  {
                SyncerConfiguration.readFromFile(File(syncerConfigPath))
            }
        }
    }

    /**
     * Configures [CordappSyncer]. [CordappSyncer] configuration is read from the path specified in the cordapp settings file and defaults
     * to ~/.corda-updates/settings.conf otherwise.
     */
    private fun syncer(syncerConfiguration : SyncerConfiguration? = null) =
            // if syncerConfiguration have not been provided explicitly - checking `syncerConfig` configuration parameter
            // if `syncerConfig` has not been provided - attempting to read the config from the CorDapp config file
            if (syncerConfiguration == null) CordappSyncer(loadSyncerConfiguration(appServiceHub))
            else CordappSyncer(syncerConfiguration)

    /**
     * Passing an instance of [AppServiceHub] to Corda transport via custom session properties
     */
    private fun additionalConfigurationProperties() = mapOf(Pair(APP_SERVICE_HUB, appServiceHub))

    private fun refreshMetadataCacheSynchronously(syncerConfiguration : SyncerConfiguration? = null, body : (syncer : CordappSyncer) -> List<ArtifactMetadata>) : List<ArtifactMetadata> {
        val artifacts : List<ArtifactMetadata> = body(syncer(syncerConfiguration))
        val artifactsMetadataCache : ArtifactsMetadataCache = appServiceHub.cordaService(ArtifactsMetadataCache::class.java)
        artifactsMetadataCache.cache = artifacts
        return artifacts
    }

    /**
     * Synchronously launches artifact synchronisation.
     */
    fun syncArtifacts(syncerConfiguration : SyncerConfiguration? = null) = refreshMetadataCacheSynchronously(syncerConfiguration) { syncer ->
        syncer.syncCordapps(additionalConfigurationProperties = additionalConfigurationProperties())
    }

    /**
     * Synchronously retrieves artifacts metadata.
     *
     * @param cordappCoordinatesWithRange coordinates of a cordapp with range, i.e. "net.corda:corda-finance:[0,2.0)"
     */
    fun getArtifactsMetadata(cordappCoordinatesWithRange : String, syncerConfiguration : SyncerConfiguration? = null) = refreshMetadataCacheSynchronously(syncerConfiguration) { syncer ->
        syncer.getAvailableVersions(cordappCoordinatesWithRange, additionalConfigurationProperties())
    }

    /**
     * Asynchronously launches artifact synchronisation
     */
    fun syncArtifactsAsync(syncerConfiguration : SyncerConfiguration? = null) {
        executor.submit(Callable {
            try {
                syncArtifacts(syncerConfiguration)
            } catch (ex : Exception) {
                logger.error("Error while syncing artifacts", ex)
            }
        })
    }

    /**
     * Asynchronously gets artifact metadata
     *
     * @param cordappCoordinatesWithRange coordinates of cordapp with range, i.e. "net.corda:corda-finance:[0,2.0)"
     */
    fun getArtifactsMetadataAsync(cordappCoordinatesWithRange : String, syncerConfiguration : SyncerConfiguration? = null) {
        executor.submit(Callable {
            try {
                getArtifactsMetadata(cordappCoordinatesWithRange, syncerConfiguration)
            } catch (ex : Exception) {
                logger.error("Error while getting artifacts metadata", ex)
            }
        })
    }
}