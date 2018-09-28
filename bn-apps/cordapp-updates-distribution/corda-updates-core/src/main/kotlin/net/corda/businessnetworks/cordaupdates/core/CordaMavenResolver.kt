package net.corda.businessnetworks.cordaupdates.core

import net.corda.cordaupdates.transport.flows.ConfigurationProperties
import net.corda.cordaupdates.transport.flows.FlowsTransporterFactory
import net.corda.cordaupdates.transport.flows.RPCTransporterFactory
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
import org.eclipse.aether.util.repository.AuthenticationBuilder

class CordaMavenResolver private constructor(private val remoteRepoUrl : String,
                                             private val localRepoPath : String,
                                             private val repoAuthentication : Authentication? = null,
                                             private val proxy : Proxy? = null,
                                             private val configProperties : Map<String, Any> = mapOf()) {

    companion object {
        fun create(remoteRepoUrl : String? = null,
                   localRepoPath : String? = null,
                   httpUsername : String? = null,
                   httpPassword : String? = null,
                   httpProxyUrl : String? = null,
                   httpProxyType : String? = null,
                   httpProxyPort : Int? = null,
                   httpProxyUsername : String? = null,
                   httpProxyPassword : String? = null,
                   rpcHost : String? = null,
                   rpcPort : String? = null,
                   rpcUsername : String? = null,
                   rpcPassword : String? = null) : CordaMavenResolver {
            // setting up authentication
            var authentication : Authentication? = null
            if (httpUsername != null && httpPassword != null) {
                authentication = AuthenticationBuilder().addUsername(httpUsername).addPassword(httpPassword).build()
            }

            // setting up proxy
            var proxy : Proxy? = null
            if (httpProxyUrl != null && httpProxyType != null && httpProxyPort != null) {
                var proxyAuthentication : Authentication? = null
                if (httpProxyPassword != null && httpProxyUsername != null) {
                    proxyAuthentication = AuthenticationBuilder().addUsername(httpProxyUsername).addPassword(httpProxyPassword).build()
                }
                proxy = Proxy(httpProxyType, httpProxyUrl, httpProxyPort, proxyAuthentication)
            }

            val configurationProperties = mutableMapOf<String, Any>()

            // RPC options
            rpcHost?.let { configurationProperties[ConfigurationProperties.RPC_HOST] = it }
            rpcPort?.let { configurationProperties[ConfigurationProperties.RPC_PORT] = it }
            rpcUsername?.let { configurationProperties[ConfigurationProperties.RPC_USERNAME] = it }
            rpcPassword?.let { configurationProperties[ConfigurationProperties.RPC_PASSWORD] = it }

            return CordaMavenResolver(remoteRepoUrl!!, localRepoPath!!, authentication, proxy, configurationProperties)
        }
    }

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
        (configProperties + additionalConfigProperties).forEach {
            session.setConfigProperty(it.key, it.value)
        }
        if (repositoryListener != null) session.repositoryListener = repositoryListener
        if (transferListener != null) session.transferListener = transferListener
        session.localRepositoryManager = SimpleLocalRepositoryManagerFactory().newInstance(session, LocalRepository(localRepoPath))
        return session
    }
}