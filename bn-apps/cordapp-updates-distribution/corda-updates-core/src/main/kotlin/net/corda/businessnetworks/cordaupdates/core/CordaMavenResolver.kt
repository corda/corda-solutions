package net.corda.businessnetworks.cordaupdates.core


import net.corda.businessnetworks.cordaupdates.core.flowstransport.FlowsTransporterFactory
import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.apache.maven.repository.internal.MavenRepositorySystemUtils.newSession
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.collection.CollectRequest
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.internal.impl.SimpleLocalRepositoryManagerFactory
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.DependencyRequest
import org.eclipse.aether.resolution.DependencyResult
import org.eclipse.aether.resolution.VersionRangeRequest
import org.eclipse.aether.resolution.VersionRangeResult
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transport.file.FileTransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory
import org.slf4j.LoggerFactory
import java.lang.Exception

class CordaMavenResolver(remoteRepoUrl : String?,
                         private val localRepoPath : String?) {
    companion object {
        val logger = LoggerFactory.getLogger(CordaMavenResolver::class.java)
    }

    private val repositorySystem : RepositorySystem by lazy {
        val locator = MavenRepositorySystemUtils.newServiceLocator()
        locator.addService<RepositoryConnectorFactory>(RepositoryConnectorFactory::class.java, BasicRepositoryConnectorFactory::class.java)
        locator.addService<TransporterFactory>(TransporterFactory::class.java, FileTransporterFactory::class.java)
        locator.addService<TransporterFactory>(TransporterFactory::class.java, HttpTransporterFactory::class.java)
        locator.addService<TransporterFactory>(TransporterFactory::class.java, FlowsTransporterFactory::class.java)
        locator.getService(RepositorySystem::class.java)
    }

    private val remoteRepository : RemoteRepository by lazy {
        RemoteRepository.Builder("remote", "default", remoteRepoUrl).build()
    }

    fun peekVersion(mavenCoords : String) : Boolean {
        val artifact = DefaultArtifact(mavenCoords)
        val modifiedVersion = artifact.setVersion("[${artifact.version}]")
        return resolveVersionRange(modifiedVersion.toString()).versions.isNotEmpty()
    }

    fun resolveVersionRange(rangeRequest : String) : VersionRangeResult {
        val versionRangeRequest = VersionRangeRequest(DefaultArtifact(rangeRequest),
                listOf(remoteRepository), null)

        val session = newSession()
        session.localRepositoryManager = SimpleLocalRepositoryManagerFactory().newInstance(session, LocalRepository(""))

        return repositorySystem.resolveVersionRange(session, versionRangeRequest)
    }

    fun downloadVersionRange(rangeRequest : String) : List<DependencyResult> {
        val versions = resolveVersionRange(rangeRequest)
        val artifact = DefaultArtifact(rangeRequest)
        return versions.versions.map {
            val artifactVersion = artifact.setVersion(it.toString())!!
            logger.info("Downloading $artifactVersion")
            downloadVersion(artifactVersion.toString())
        }
    }

    fun downloadVersion(mavenCoords : String) : DependencyResult {
        val session = newSession()

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
}

class CordappDownloaderException(message : String) : Exception(message)