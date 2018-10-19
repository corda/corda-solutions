package net.corda.businessnetworks.cordaupdates.core

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.eclipse.aether.RepositoryListener
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.transfer.TransferListener
import java.io.File

/**
 * A wrapper around [CordaMavenResolver] that can sync different CorDapps from different remote repositories via single method invocation
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
     * @param coordinatesWithRange full maven coordinates of a CorDapp with a version range, i.e. "net.corda:corda-finance:[0,2.5)".
     * @param additionalConfigurationProperties additional parameters to be passed to [CordaMavenResolver]
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
 * Configuration for [CordappSyncer]. Usually is red from settings.conf
 */
data class SyncerConfiguration(
        val localRepoPath : String,
        val httpProxyHost : String? = null,
        val httpProxyType : String? = null,
        val httpProxyPort : Int? = null,
        val httpProxyUsername : String? = null,
        val httpProxyPassword : String? = null,
        val rpcHost : String? = null,
        val rpcPort : Int? = null,
        val rpcUsername : String? = null,
        val rpcPassword : String? = null,
        val cordappSources : List<CordappSource>) {
    companion object {
        fun readFromFile(file : File) : SyncerConfiguration {

            val conf = ConfigFactory.parseFile(file)!!
            val cordapps = conf.getObjectList("cordappSources").asSequence().map { it.toConfig()!! }.map { CordappSource(
                    it.getString("remoteRepoUrl"),
                    it.getStringList("cordapps"),
                    it.getStringOrNull("httpUsername"),
                    it.getStringOrNull("httpPassword")
            ) }.toList()

            return SyncerConfiguration(
                    conf.getString("localRepoPath"),
                    conf.getStringOrNull("httpProxyHost"),
                    conf.getStringOrNull("httpProxyType"),
                    conf.getIntOrNull("httpProxyPort"),
                    conf.getStringOrNull("httpProxyUsername"),
                    conf.getStringOrNull("httpProxyPassword"),
                    conf.getStringOrNull("rpcHost"),
                    conf.getIntOrNull("rpcPort"),
                    conf.getStringOrNull("rpcUsername"),
                    conf.getStringOrNull("rpcPassword"),
                    cordapps
            )
        }

        private fun Config.getStringOrNull(key : String) = if (hasPath(key)) getString(key) else null
        private fun Config.getIntOrNull(key : String) = if (hasPath(key)) getInt(key) else null
    }

    fun findSourceFor(cordapp : String) : CordappSource? {
        val artifact = DefaultArtifact(cordapp)
        return cordappSources.firstOrNull { it.cordapps.contains("${artifact.groupId}:${artifact.artifactId}") }
    }

}

open class SyncerException(message : String? = null, cause : Throwable? = null) : Exception(message, cause)
class CordappSourceNotFoundException(cordapp : String) : SyncerException("Cordapp source has not been found for cordapp $cordapp")