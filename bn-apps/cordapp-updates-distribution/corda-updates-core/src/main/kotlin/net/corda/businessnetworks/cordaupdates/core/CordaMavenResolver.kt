package net.corda.businessnetworks.cordaupdates.core

import net.corda.cordaupdates.transport.ConfigurationProperties
import net.corda.cordaupdates.transport.CordaTransporterFactory
import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.apache.maven.repository.internal.MavenRepositorySystemUtils.newSession
import org.eclipse.aether.DefaultRepositorySystemSession
import org.eclipse.aether.RepositoryException
import org.eclipse.aether.RepositoryListener
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory
import org.eclipse.aether.internal.impl.SimpleLocalRepositoryManagerFactory
import org.eclipse.aether.repository.Authentication
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.repository.Proxy
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.ArtifactRequest
import org.eclipse.aether.resolution.ArtifactResult
import org.eclipse.aether.resolution.VersionRangeRequest
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transfer.ArtifactNotFoundException
import org.eclipse.aether.transfer.ArtifactTransferException
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
                   httpProxyHost : String? = null,
                   httpProxyType : String? = null,
                   httpProxyPort : Int? = null,
                   httpProxyUsername : String? = null,
                   httpProxyPassword : String? = null,
                   rpcHost : String? = null,
                   rpcPort : String? = null,
                   rpcUsername : String? = null,
                   rpcPassword : String? = null,
                   repositoryListener : RepositoryListener? = null,
                   transferListener : TransferListener? = null) : CordaMavenResolver {
            // setting up authentication
            var authentication : Authentication? = null
            if (httpUsername != null && httpPassword != null) {
                authentication = AuthenticationBuilder().addUsername(httpUsername).addPassword(httpPassword).build()
            }

            // setting up proxy
            var proxy : Proxy? = null
            if (httpProxyHost != null && httpProxyType != null && httpProxyPort != null) {
                var proxyAuthentication : Authentication? = null
                if (httpProxyPassword != null && httpProxyUsername != null) {
                    proxyAuthentication = AuthenticationBuilder().addUsername(httpProxyUsername).addPassword(httpProxyPassword).build()
                }
                proxy = Proxy(httpProxyType, httpProxyHost, httpProxyPort, proxyAuthentication)
            }

            val configurationProperties = mutableMapOf<String, Any>()

            // RPC options
            rpcHost?.let { configurationProperties[ConfigurationProperties.RPC_HOST] = it }
            rpcPort?.let { configurationProperties[ConfigurationProperties.RPC_PORT] = it }
            rpcUsername?.let { configurationProperties[ConfigurationProperties.RPC_USERNAME] = it }
            rpcPassword?.let { configurationProperties[ConfigurationProperties.RPC_PASSWORD] = it }

            val resolver = CordaMavenResolver(remoteRepoUrl!!, localRepoPath!!, authentication, proxy, configurationProperties)
            resolver.repositoryListener = repositoryListener
            resolver.transferListener = transferListener

            return resolver
        }

        fun create(syncerConf : SyncerConfiguration,
                   cordappSource : CordappSource,
                   repositoryListener : RepositoryListener? = null,
                   transferListener : TransferListener? = null) =
                create(remoteRepoUrl = cordappSource.remoteRepoUrl,
                        localRepoPath = syncerConf.localRepoPath,
                        httpUsername = cordappSource.httpUsername,
                        httpPassword = cordappSource.httpPassword,
                        httpProxyHost = syncerConf.httpProxyHost,
                        httpProxyType = syncerConf.httpProxyType,
                        httpProxyPort = syncerConf.httpProxyPort,
                        httpProxyUsername = syncerConf.httpProxyUsername,
                        httpProxyPassword = syncerConf.httpProxyPassword,
                        rpcHost = syncerConf.rpcHost,
                        rpcPort = syncerConf.rpcPort,
                        rpcUsername = syncerConf.rpcUsername,
                        rpcPassword = syncerConf.rpcPassword,
                        repositoryListener = repositoryListener,
                        transferListener = transferListener)
    }

    var repositoryListener : RepositoryListener? = null
    var transferListener : TransferListener? = null

    private val repositorySystem : RepositorySystem by lazy {
        val locator = MavenRepositorySystemUtils.newServiceLocator()
        locator.addService<RepositoryConnectorFactory>(RepositoryConnectorFactory::class.java, BasicRepositoryConnectorFactory::class.java)
        locator.addService<TransporterFactory>(TransporterFactory::class.java, FileTransporterFactory::class.java)
        locator.addService<TransporterFactory>(TransporterFactory::class.java, HttpTransporterFactory::class.java)
        locator.addService<TransporterFactory>(TransporterFactory::class.java, CordaTransporterFactory::class.java)
        locator.getService(RepositorySystem::class.java)
    }

    private val remoteRepository : RemoteRepository by lazy {
        RemoteRepository.Builder("remote", "default", remoteRepoUrl)
                .setAuthentication(repoAuthentication)
                .setProxy(proxy)
                .build()
    }

    fun resolveVersionRange(rangeRequest : String,
                            configProps : Map<String, Any> = mapOf()) : ArtifactMetadata {
        val artifact = DefaultArtifact(rangeRequest)
        val versionRangeRequest = VersionRangeRequest(artifact, listOf(remoteRepository), null)
        val session = createRepositorySession(configProps)
        val versionRangeResult = repositorySystem.resolveVersionRange(session, versionRangeRequest)!!

        return ArtifactMetadata(artifact.groupId,
                artifact.artifactId,
                artifact.classifier,
                artifact.extension,
                versionRangeResult.versions.map { VersionMetadata(it.toString(), versionRangeResult.getRepository(it) is LocalRepository) })
    }

    fun downloadVersionRange(rangeRequest : String,
                             configProps : Map<String, Any> = mapOf()) : ArtifactMetadata {
        val artifactMetadata = resolveVersionRange(rangeRequest, configProps)

        val aetherArtifact = DefaultArtifact(rangeRequest)

        // downloading the most recent version first
        val versions = artifactMetadata.versions.asReversed().map {
            val artifactVersion = aetherArtifact.setVersion(it.version)!!
            downloadVersion(artifactVersion.toString(), configProps).versions.single()
        }.asReversed()
        return artifactMetadata.copy(versions = versions)
    }

    fun downloadVersion(mavenCoords : String,
                        configProps : Map<String, Any> = mapOf()) : ArtifactMetadata {
        val session = createRepositorySession(configProps)
        val aetherArtifact = DefaultArtifact(mavenCoords)
        val artifactRequest = ArtifactRequest(aetherArtifact, listOf(remoteRepository), null)
        val resolutionResult : ArtifactResult
        try {
            resolutionResult = repositorySystem.resolveArtifact(session, artifactRequest)!!
        } catch (ex : RepositoryException) {
            when (ex.cause) {
                is ArtifactNotFoundException -> throw ResourceNotFoundException(mavenCoords, ex)
                is ArtifactTransferException -> throw ResourceTransferException(mavenCoords, ex)
                else -> throw CordaMavenResolverException("Error while resolving the artifact $mavenCoords", ex)
            }
        }
        return ArtifactMetadata(aetherArtifact.groupId,
                aetherArtifact.artifactId,
                aetherArtifact.classifier,
                aetherArtifact.extension,
                listOf(VersionMetadata(aetherArtifact.version, resolutionResult.repository is LocalRepository)))
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

data class ArtifactMetadata(val group : String, val name : String, val classifier : String = "", val extension : String = "jar", val versions : List<VersionMetadata> = listOf()) {
    fun toMavenArtifacts() = versions.map { DefaultArtifact(group, name, classifier, extension, it.version) }
}

data class VersionMetadata(val version : String, val isFromLocal : Boolean)

open class CordaMavenResolverException(message : String, cause : Throwable) : Exception(message, cause)
class ResourceNotFoundException(val resource : String, cause : Throwable) : CordaMavenResolverException("Resource $resource has not been found", cause)
class ResourceTransferException(val resource : String, cause : Throwable) : CordaMavenResolverException("Resource $resource has not been found", cause)