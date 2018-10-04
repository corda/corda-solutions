package net.corda.businessnetworks.cordaupdates.core

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.eclipse.aether.artifact.DefaultArtifact
import java.io.File
import java.lang.IllegalArgumentException
import java.nio.file.Files

class CordappSyncer(private val syncerConf : SyncerConfiguration) {
    fun syncCordapps(additionalConfigurationProperties : Map<String, Any> = mapOf(), cordappToSync : String? = null) : List<ArtifactMetadata> {
        return if (cordappToSync == null) {
            syncerConf.cordappSources.flatMap { syncTask ->
                val resolver = CordaMavenResolver.create(syncerConf, syncTask)
                syncTask.cordapps.map {
                    resolver.downloadVersionRange("$it:[,)", additionalConfigurationProperties)
                }
            }
        } else {
            val source = syncerConf.findSourceFor(cordappToSync) ?: throw CordappSourceNotFoundException(cordappToSync)
            val resolver = CordaMavenResolver.create(syncerConf, source)
            listOf(resolver.downloadVersionRange(cordappToSync, additionalConfigurationProperties))
        }
    }

    fun getAvailableVersions(additionalConfigurationProperties : Map<String, Any> = mapOf(), cordapp : String? = null) : List<ArtifactMetadata> {
        return if (cordapp == null) {
            syncerConf.cordappSources.flatMap { syncTask ->
                val resolver = CordaMavenResolver.create(syncerConf, syncTask)
                syncTask.cordapps.map {
                    resolver.resolveVersionRange("$it:[,)", additionalConfigurationProperties)
                }
            }
        } else {
            val syncTask = syncerConf.findSourceFor(cordapp) ?: throw CordappSourceNotFoundException(cordapp)
            val resolver = CordaMavenResolver.create(syncerConf, syncTask)
            listOf(resolver.resolveVersionRange(cordapp, additionalConfigurationProperties))
        }
    }
}

data class CordappSource(
        val remoteRepoUrl : String,
        val cordapps : List<String>,
        val httpUsername : String? = null,
        val httpPassword : String? = null)

data class SyncerConfiguration(
        val localRepoPath : String,
        val httpProxyUrl : String? = null,
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
        fun readFromFileOrDefaultLocations(file : String? = null) : SyncerConfiguration {
            return if (file != null) readFromFile(File(file)) else readFromDefaultLocations()
        }

        fun readFromDefaultLocations() : SyncerConfiguration {
            val localConfig = File(".corda-updates/settings.yaml")
            if (localConfig.exists()) return readFromFile(localConfig)
            val userConfig = File("${System.getProperty("user.home")}/.corda-updates/settings.yaml")
            if (userConfig.exists()) return readFromFile(localConfig)
            throw SyncerConfigurationNotFoundException()
        }

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
class SyncerConfigurationNotFoundException : SyncerException()
class CordappSourceNotFoundException(cordapp : String) : SyncerException("Cordapp source has not been found for cordapp $cordapp")