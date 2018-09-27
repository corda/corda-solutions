package net.corda.businessnetworks.cordaupdates.core

import net.corda.cordaupdates.transport.flows.ConfigurationProperties
import net.corda.cordaupdates.transport.flows.FlowsTransporterFactory
import net.corda.cordaupdates.transport.flows.RPCTransporterFactory
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.apache.maven.repository.internal.MavenRepositorySystemUtils.newSession
import org.eclipse.aether.DefaultRepositorySystemSession
import org.eclipse.aether.RepositoryListener
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.collection.CollectRequest
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.internal.impl.SimpleLocalRepositoryManagerFactory
import org.eclipse.aether.repository.Authentication
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.repository.Proxy
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.DependencyRequest
import org.eclipse.aether.resolution.DependencyResult
import org.eclipse.aether.resolution.VersionRangeRequest
import org.eclipse.aether.resolution.VersionRangeResult
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transfer.TransferListener
import org.eclipse.aether.transport.file.FileTransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Basic interface over maven resolver
 */

class CordaMavenResolver(private val remoteRepoUrl : String,
                         private val localRepoPath : String,
                         private val repoAuthentication : Authentication? = null,
                         private val proxy: Proxy? = null) {
    var repositoryListener : RepositoryListener? = null
    var transferListener : TransferListener? = null

    private val repositorySystem : RepositorySystem by lazy {
        val locator = MavenRepositorySystemUtils.newServiceLocator()
        locator.addService<RepositoryConnectorFactory>(RepositoryConnectorFactory::class.java, BasicRepositoryConnectorFactory::class.java)
        locator.addService<TransporterFactory>(TransporterFactory::class.java, FileTransporterFactory::class.java)
        locator.addService<TransporterFactory>(TransporterFactory::class.java, HttpTransporterFactory::class.java)
        locator.addService<TransporterFactory>(TransporterFactory::class.java, FlowsTransporterFactory::class.java)
        locator.addService<TransporterFactory>(TransporterFactory::class.java, RPCTransporterFactory::class.java)
        locator.getService(RepositorySystem::class.java)
    }

    private val remoteRepository : RemoteRepository by lazy {
        RemoteRepository.Builder("remote", "default", remoteRepoUrl)
                .setAuthentication(repoAuthentication)
                .setProxy(proxy)
                .build()
    }

    fun resolveVersionRange(rangeRequest : String,
                            configProps : Map<String, Any> = mapOf()) : VersionRangeResult {
        val versionRangeRequest = VersionRangeRequest(DefaultArtifact(rangeRequest),
                listOf(remoteRepository), null)

        val session = createRepositorySession(configProps)
        session.localRepositoryManager = SimpleLocalRepositoryManagerFactory().newInstance(session, LocalRepository(""))

        return repositorySystem.resolveVersionRange(session, versionRangeRequest)
    }

    fun downloadVersionRange(rangeRequest : String,
                             configProps : Map<String, Any> = mapOf()) : List<DependencyResult> {
        val versions = resolveVersionRange(rangeRequest, configProps)
        val artifact = DefaultArtifact(rangeRequest)
        return versions.versions.map {
            val artifactVersion = artifact.setVersion(it.toString())!!
            downloadVersion(artifactVersion.toString(), configProps)
        }
    }

    fun downloadVersion(mavenCoords : String,
                        configProps : Map<String, Any> = mapOf()) : DependencyResult {
        val session = createRepositorySession(configProps)

        session.localRepositoryManager = SimpleLocalRepositoryManagerFactory().newInstance(session, LocalRepository(localRepoPath))

        val dependency = Dependency(DefaultArtifact(mavenCoords), "compile")

        val collectRequest = CollectRequest()
        collectRequest.root = dependency
        collectRequest.addRepository(remoteRepository)
        val node = repositorySystem.collectDependencies(session, collectRequest).root

        val dependencyRequest = DependencyRequest()
        dependencyRequest.root = node

        return repositorySystem.resolveDependencies(session, dependencyRequest)
    }

    private fun createRepositorySession(additionalConfigProperties : Map<String, Any>) : DefaultRepositorySystemSession {
        val session = newSession()!!
        additionalConfigProperties.forEach {
            session.setConfigProperty(it.key, it.value)
        }
        if (repositoryListener != null) session.repositoryListener = repositoryListener
        if (transferListener != null) session.transferListener = transferListener
        return session
    }
}

@CordaService
class CordaMavenResolverService(private val appServiceHub : AppServiceHub) : SingletonSerializeAsToken() {
    companion object {
        val executor = Executors.newSingleThreadExecutor()!!
    }

    fun resolveVersionRangeAsync(remoteRepoUrl : String, localRepoPath : String, rangeRequest : String, onComplete : (result : VersionRangeResult) -> Unit = {}) {
        executor.submit(Callable {
            val resolver = CordaMavenResolver(remoteRepoUrl, localRepoPath)
            val result = resolver.resolveVersionRange(rangeRequest, configProps = mapOf(Pair(ConfigurationProperties.APP_SERVICE_HUB, appServiceHub)))
            onComplete(result)
        })
    }

    fun downloadVersionRangeAsync(remoteRepoUrl : String, localRepoPath : String, rangeRequest : String, onComplete : (result : List<DependencyResult>) -> Unit = {}) {
        executor.submit(Callable {
            val resolver = CordaMavenResolver(remoteRepoUrl, localRepoPath)
            val result = resolver.downloadVersionRange(rangeRequest, configProps = mapOf(Pair(ConfigurationProperties.APP_SERVICE_HUB, appServiceHub)))
            onComplete(result)
        })
    }

    fun downloadVersionAsync(remoteRepoUrl : String, localRepoPath : String, mavenCoords : String, onComplete : (result : DependencyResult) -> Unit = {}) {
        executor.submit(Callable {
            val resolver = CordaMavenResolver(remoteRepoUrl, localRepoPath)
            val result = resolver.downloadVersion(mavenCoords, configProps = mapOf(Pair(ConfigurationProperties.APP_SERVICE_HUB, appServiceHub)))
            onComplete(result)
        })
    }
}