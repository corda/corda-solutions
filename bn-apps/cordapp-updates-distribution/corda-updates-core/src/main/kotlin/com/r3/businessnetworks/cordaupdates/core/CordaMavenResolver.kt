package com.r3.businessnetworks.cordaupdates.core

import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.loggerFor
import com.r3.businessnetworks.cordaupdates.transport.CordaTransporterFactory
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
import org.eclipse.aether.resolution.VersionRangeResult
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transfer.ArtifactNotFoundException
import org.eclipse.aether.transfer.ArtifactTransferException
import org.eclipse.aether.transfer.TransferListener
import org.eclipse.aether.transport.file.FileTransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory
import org.eclipse.aether.util.repository.AuthenticationBuilder

/**
 * Wrapper around Maven Resolver that allows to:
 *  - download a single version / version range of a CorDapp from a remote repository
 *  - get a list of CorDapp versions available in a remote repository
 *
 * Downloading a version range is effectively a combination of getting available versions + downloading each missing version separately.
 * Version ranges can be specified using mathematical range notation, i.e. "(1,2.5]".
 *
 * CordaMavenResolver utilises Maven's local and remote repositories concepts, as well as other standard Maven mechanics.
 * For example it will not attempt to re-download an artifact if it already exists in the local repo.
 *
 * CordaMavenResolver supports basic authentication for HTTP(s) proxies and remote repositories.
 *
 * CordaMavenResolver supports standard file, http(s) transports as well as a bespoke Corda transport.
 * For more details @see [CordaTransporterFactory].
 */

class CordaMavenResolver private constructor(private val remoteRepoUrl : String,
                                             private val localRepoPath : String,
                                             private val repoAuthentication : Authentication? = null,
                                             private val proxy : Proxy? = null,
                                             private val configProperties : Map<String, Any> = mapOf()) {
    companion object {
        val logger = loggerFor<CordaMavenResolver>()

        fun create(remoteRepoUrl : String? = null,
                   localRepoPath : String? = null,
                   httpUsername : String? = null,
                   httpPassword : String? = null,
                   httpProxyHost : String? = null,
                   httpProxyType : String? = null,
                   httpProxyPort : Int? = null,
                   httpProxyUsername : String? = null,
                   httpProxyPassword : String? = null,
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

            // listeners can be used to report Maven Resolver progress
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

    /**
     * Resolves a list of CorDapps versions available in remote repositories.
     *
     * For version range requests, isFromLocal field is always equal to false, regardless of whether a version existed in the local repository.
     *
     * @param coordinatesWithRange full maven coordinates with range in standard Maven notation, i.e. "net.cord:corda-finance:(1,2.5]".
     * @param configProps will be passed as custom Maven Resolver session properties
     *
     * @return metadata for the available versions. If no versions have been found [ArtifactMetadata.versions] list will be empty
     */
    fun resolveVersionRange(coordinatesWithRange : String,
                            configProps : Map<String, Any> = mapOf()) : ArtifactMetadata {
        val artifact = DefaultArtifact(coordinatesWithRange)
        val versionRangeRequest = VersionRangeRequest(artifact, listOf(remoteRepository), null)
        val session : DefaultRepositorySystemSession = createRepositorySession(configProps)
        val versionRangeResult : VersionRangeResult = repositorySystem.resolveVersionRange(session, versionRangeRequest)!!

        if (versionRangeResult.versions.isEmpty()) {
            logger.warn("Failed to resolve version for $coordinatesWithRange. Exceptions: ${versionRangeResult.exceptions}")
        }

        return ArtifactMetadata(artifact.groupId,
                artifact.artifactId,
                artifact.classifier,
                artifact.extension,
                versionRangeResult.versions.map { VersionMetadata(it.toString(), versionRangeResult.getRepository(it) is LocalRepository) })
    }

    /**
     * Downloads all locally missing CorDapp versions from the remote repository. For the existing versions isFromLocal field will be equal to true.
     * This method is effectively a combination of resolveVersionRange + downloadVersion.
     *
     * @param coordinatesWithRange full maven coordinates with the range in standard Maven notation, i.e. "net.cord:corda-finance:(1,2.5]".
     * @param configProps will be passed as custom Maven Resolver session properties
     *
     * @return metadata for all of the versions (both existing and downloaded). isFromLocal field indicates whether a version
     *      existed locally or not
     */
    fun downloadVersionRange(coordinatesWithRange : String,
                             configProps : Map<String, Any> = mapOf()) : ArtifactMetadata {
        logger.info("Syncing cordapps $coordinatesWithRange")
        val artifactMetadata : ArtifactMetadata = resolveVersionRange(coordinatesWithRange, configProps)

        val aetherArtifact = DefaultArtifact(coordinatesWithRange)

        // downloading the most recent version first
        // .toList() after .asReversed() is required as ReversedReadOnlyList is not CordaSerialisable
        val versions : List<VersionMetadata> = artifactMetadata.versions.asReversed().map {
            val artifactVersion = aetherArtifact.setVersion(it.version)!!
            downloadVersion(artifactVersion.toString(), configProps).versions.single()
        }.asReversed().toList()
        return artifactMetadata.copy(versions = versions).apply {
            val downloaded = versions.filter { !it.isFromLocal }
            if (downloaded.isEmpty()) {
                logger.info("No new cordapps have been found")
            } else {
                logger.info("Downloaded cordapps: $downloaded")
            }
        }
    }

    /**
     * Downloads a specified version of a CorDapp if it's missing in the local repo.
     *
     * @param coordinates full maven coordinates, i.e. "net.corda:corda-finance:3.2".
     * @param configProps will be passed as custom Maven Resolver session properties
     *
     * @return artifact metadata with a single version
     *
     * @throws [ResourceNotFoundException] if the the specified artifact has not been found in the remote repository
     * @throws [ResourceTransferException] if the remote repository is unreachable
     */
    fun downloadVersion(coordinates : String,
                        configProps : Map<String, Any> = mapOf()) : ArtifactMetadata {
        val session : DefaultRepositorySystemSession = createRepositorySession(configProps)
        val aetherArtifact = DefaultArtifact(coordinates)
        val artifactRequest = ArtifactRequest(aetherArtifact, listOf(remoteRepository), null)
        val resolutionResult : ArtifactResult
        try {
            resolutionResult = repositorySystem.resolveArtifact(session, artifactRequest)!!
        } catch (ex : RepositoryException) {
            when (ex.cause) {
                is ArtifactNotFoundException -> throw ResourceNotFoundException(coordinates, ex)
                is ArtifactTransferException -> throw ResourceTransferException(coordinates, ex)
                else -> throw GeneralResolverException("Error while resolving the artifact $coordinates", ex)
            }
        }
        return ArtifactMetadata(aetherArtifact.groupId,
                aetherArtifact.artifactId,
                aetherArtifact.classifier,
                aetherArtifact.extension,
                listOf(VersionMetadata(aetherArtifact.version, resolutionResult.repository is LocalRepository)))
    }

    private fun createRepositorySession(additionalConfigProperties : Map<String, Any>) : DefaultRepositorySystemSession {
        val session : DefaultRepositorySystemSession = newSession()!!
        (configProperties + additionalConfigProperties).forEach {
            session.setConfigProperty(it.key, it.value)
        }
        if (repositoryListener != null) session.repositoryListener = repositoryListener
        if (transferListener != null) session.transferListener = transferListener
        session.localRepositoryManager = SimpleLocalRepositoryManagerFactory().newInstance(session, LocalRepository(localRepoPath))
        return session
    }
}

/**
 * Representation of an artifact and its versions.
 */
@CordaSerializable
data class ArtifactMetadata(val group : String, val name : String, val classifier : String = "", val extension : String = "jar", val versions : List<VersionMetadata> = listOf())
/**
 * Representation of an artifact version. isFromLocal field indicates whether the version was resolved from the local repository.
 */
@CordaSerializable
data class VersionMetadata(val version : String, val isFromLocal : Boolean)

/**
 * Wrapper around Aether's [RepositoryException]. The aim is to provide a clear exception hierarchy to Aether exception model
 * so the calling site can act accordingly.
 */
sealed class CordaMavenResolverException(message : String, cause : RepositoryException) : Exception(message, cause)

/**
 * Thrown when a resource is not found in the remote repository
 */
class ResourceNotFoundException(val resource : String, cause : RepositoryException) : CordaMavenResolverException("Resource $resource has not been found", cause)

/**
 * Thrown when a transfer exception occurs. I.e. no connection, invalid proxy settings etc.
 */
class ResourceTransferException(val resource : String, cause : RepositoryException) : CordaMavenResolverException("Resource $resource has not been found", cause)

/**
 * Thrown if the cause is neither of the above.
 */
class GeneralResolverException(message : String, cause : RepositoryException) : CordaMavenResolverException(message, cause)
