package net.corda.businessnetworks.cordaupdates.core

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.eclipse.aether.RepositoryListener
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.transfer.TransferListener
import java.io.File
import java.lang.IllegalArgumentException
import java.nio.file.Files

/**
 * A wrapper around [CordaMavenResolver] that can be sync different CorDapps from different remote repositories via single method invocation
 *
 * @syncerCong syncer configuration
 * @repositoryListener repository listener that will be passed to an underlying [CordaMavenResolver] instance
 * @transferListener transfer listener that will be passed to an underlying [CordaMavenResolver] instance
 */
class CordappSyncer(private val syncerConf : SyncerConfiguration,
                    private val repositoryListener : RepositoryListener? = null,
                    private val transferListener : TransferListener? = null) {

    /**
     * Downloads locally missing CorDapps versions from the remote repositories.
     *
     * @coordinatesWithoutVersion optional maven coordinates of a CorDapp, without a version i.e. "net.corda:corda-finance".
     *              If not provided, then all missing versions of all CorDapps defined in the [syncerConf] will be downloaded.
     *              If provided, then [syncerConf] is expected to contain a configured [CordappSource] for the CorDapp coordinates.
     * @additionalConfigurationProperties additional parameters to be passed to [CordaMavenResolver]
     * @return a list of CorDapp metadatas that have been resolved from local / remote repositories
     *
     * @throws [CordappSourceNotFoundException] if CorDapp source has not been found
     */
    fun syncCordapps(coordinatesWithoutVersion : String? = null, additionalConfigurationProperties : Map<String, Any> = mapOf()) : List<ArtifactMetadata> {
        return if (coordinatesWithoutVersion == null) {
            syncerConf.cordappSources.flatMap { syncTask ->
                val resolver = CordaMavenResolver.create(syncerConf, syncTask, repositoryListener = repositoryListener, transferListener = transferListener)
                syncTask.cordapps.map {
                    resolver.downloadVersionRange("$it:[,)", additionalConfigurationProperties)
                }
            }
        } else {
            val source = syncerConf.findSourceFor(coordinatesWithoutVersion) ?: throw CordappSourceNotFoundException(coordinatesWithoutVersion)
            val resolver = CordaMavenResolver.create(syncerConf, source)
            listOf(resolver.downloadVersionRange(coordinatesWithoutVersion, additionalConfigurationProperties))
        }
    }

    /**
     * Returns a list of CorDapp versions available in the remote repository. syncerConf is expected to contain a configured [CordappSource] for the requested CorDapp.
     *
     * @coordinatesWithRange full maven coordinates of a CorDapp with a version range, i.e. "net.corda:corda-finance:[0,2.5)".
     * @additionalConfigurationProperties additional parameters to be passed to [CordaMavenResolver]
     */
    fun getAvailableVersions(coordinatesWithRange : String, additionalConfigurationProperties : Map<String, Any> = mapOf()) : List<ArtifactMetadata> {
        val syncTask = syncerConf.findSourceFor(coordinatesWithRange) ?: throw CordappSourceNotFoundException(coordinatesWithRange)
        val resolver = CordaMavenResolver.create(syncerConf, syncTask)
        return listOf(resolver.resolveVersionRange(coordinatesWithRange, additionalConfigurationProperties))
    }
}

/**
 * [CordappSource] allows, that allows to associate multiple CorDapps with a remote repository
 */
data class CordappSource(
        val remoteRepoUrl : String,
        val cordapps : List<String>,
        val httpUsername : String? = null,
        val httpPassword : String? = null)

/**
 * Configuration for [CordappSyncer]. Usually is red from settings.yaml
 */
data class SyncerConfiguration(
        val localRepoPath : String,
        val httpProxyHost : String? = null,
        val httpProxyType : String? = null,
        val httpProxyPort : Int? = null,
        val httpProxyUsername : String? = null,
        val httpProxyPassword : String? = null,
        val rpcHost : String? = null,
        val rpcPort : String? = null,
        val rpcUsername : String? = null,
        val rpcPassword : String? = null,
        val cordappSources : List<CordappSource>) {
    companion object {
        fun readFromFile(file : File) : SyncerConfiguration {
            val mapper = when {
                file.name.endsWith("yaml") -> ObjectMapper(YAMLFactory())
                else -> throw IllegalArgumentException("Unsupported file format $file")
            }
            mapper.registerModule(KotlinModule())
            return Files.newBufferedReader(file.toPath()).use {
                mapper.readValue(it, SyncerConfiguration::class.java)
            }
        }
    }

    fun findSourceFor(cordapp : String) : CordappSource? {
        val artifact = DefaultArtifact(cordapp)
        return cordappSources.firstOrNull { it.cordapps.contains("${artifact.groupId}:${artifact.artifactId}") }
    }
}

open class SyncerException(message : String? = null, cause : Throwable? = null) : Exception(message, cause)
class CordappSourceNotFoundException(cordapp : String) : SyncerException("Cordapp source has not been found for cordapp $cordapp")