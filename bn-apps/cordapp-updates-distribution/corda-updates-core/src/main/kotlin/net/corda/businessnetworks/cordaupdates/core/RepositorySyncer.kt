package net.corda.businessnetworks.cordaupdates.core

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.io.File
import java.lang.IllegalArgumentException
import java.nio.file.Files


class RepositorySyncer(private val syncerConf : SyncerConfiguration) {
    fun sync(additionalConfigurationProperties : Map<String, Any> = mapOf()) =
        syncerConf.tasks.flatMap { syncTask ->
            val resolver = CordaMavenResolver.create(syncerConf, syncTask)
            syncTask.artifacts.map {
                resolver.downloadVersionRange("$it:[,)", additionalConfigurationProperties)
            }
        }
}

data class SyncerTask(val remoteRepoUrl : String,
                      val artifacts : List<String>,
                      val httpUsername : String? = null,
                      val httpPassword : String? = null,
                      val syncLatestOnly : Boolean = false)

data class SyncerConfiguration(val localRepoPath : String,
                               val httpProxyUrl : String? = null,
                               val httpProxyType : String? = null,
                               val httpProxyPort : Int? = null,
                               val httpProxyUsername : String? = null,
                               val httpProxyPassword : String? = null,
                               val rpcHost : String? = null,
                               val rpcPort : String? = null,
                               val rpcUsername : String? = null,
                               val rpcPassword : String? = null,
                               val tasks : List<SyncerTask>) {
    companion object {
        fun readFromDefaultLocations() : SyncerConfiguration  {
            val localConfig = File(".corda-updates/config.yaml")
            if (localConfig.exists()) return readFromFile(localConfig)
            val userConfig = File("${System.getProperty("user.home")}/.corda-updates/config.yaml")
            if (userConfig.exists()) return readFromFile(localConfig)
            throw NoConfigFoundException()
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
}

class NoConfigFoundException : Exception()