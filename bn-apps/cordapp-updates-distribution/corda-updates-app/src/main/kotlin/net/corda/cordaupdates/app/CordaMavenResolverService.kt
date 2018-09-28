package net.corda.cordaupdates.app

import net.corda.businessnetworks.cordaupdates.core.CordaMavenResolver
import net.corda.cordaupdates.transport.flows.ConfigurationProperties
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import org.eclipse.aether.resolution.DependencyResult
import org.eclipse.aether.resolution.VersionRangeResult
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@CordaService
class CordaMavenResolverService(private val appServiceHub : AppServiceHub) : SingletonSerializeAsToken() {
    companion object {
        val executor = Executors.newSingleThreadExecutor()!!
    }

    fun resolveVersionRangeAsync(rangeRequest : String, onComplete : (result : VersionRangeResult) -> Unit = {}) {
        val configuration = appServiceHub.cordaService(ClientConfiguration::class.java)
        executor.submit(Callable {
            val resolver = createMavenResolver(ClientConfiguration.PROPERTIES_PREFIX, configuration.config)
            val result = resolver.resolveVersionRange(rangeRequest, configProps = mapOf(Pair(ConfigurationProperties.APP_SERVICE_HUB, appServiceHub)))
            onComplete(result)
        })
    }

    fun downloadVersionRangeAsync(rangeRequest : String, onComplete : (result : List<DependencyResult>) -> Unit = {}) {
        val configuration = appServiceHub.cordaService(ClientConfiguration::class.java)
        executor.submit(Callable {
            val resolver = createMavenResolver(ClientConfiguration.PROPERTIES_PREFIX, configuration.config)
            val result = resolver.downloadVersionRange(rangeRequest, configProps = mapOf(Pair(ConfigurationProperties.APP_SERVICE_HUB, appServiceHub)))
            onComplete(result)
        })
    }

    fun downloadVersionAsync(mavenCoords : String, onComplete : (result : DependencyResult) -> Unit = {}) {
        val configuration = appServiceHub.cordaService(ClientConfiguration::class.java)
        executor.submit(Callable {
            val resolver = createMavenResolver(ClientConfiguration.PROPERTIES_PREFIX, configuration.config)
            val result = resolver.downloadVersion(mavenCoords, configProps = mapOf(Pair(ConfigurationProperties.APP_SERVICE_HUB, appServiceHub)))
            onComplete(result)
        })
    }
}

fun createMavenResolver(configurationPrefix : String, properties : Map<String, Any>) : CordaMavenResolver = CordaMavenResolver.create(
        remoteRepoUrl = properties["$configurationPrefix.remoteRepoUrl"]?.toString(),
        localRepoPath = properties["$configurationPrefix.remoteRepoUrl"]?.toString(),
        httpUsername = properties["$configurationPrefix.remoteRepoUrl"]?.toString(),
        httpPassword = properties["$configurationPrefix.remoteRepoUrl"]?.toString(),
        httpProxyUrl = properties["$configurationPrefix.remoteRepoUrl"]?.toString(),
        httpProxyType = properties["$configurationPrefix.remoteRepoUrl"]?.toString(),
        httpProxyPort = properties["$configurationPrefix.remoteRepoUrl"]?.toString()?.toIntOrNull(),
        httpProxyUsername = properties["$configurationPrefix.remoteRepoUrl"]?.toString(),
        httpProxyPassword = properties["$configurationPrefix.remoteRepoUrl"]?.toString(),
        rpcHost = properties["$configurationPrefix.remoteRepoUrl"]?.toString(),
        rpcPort = properties["$configurationPrefix.remoteRepoUrl"]?.toString(),
        rpcUsername = properties["$configurationPrefix.remoteRepoUrl"]?.toString(),
        rpcPassword = properties["$configurationPrefix.remoteRepoUrl"]?.toString()
)