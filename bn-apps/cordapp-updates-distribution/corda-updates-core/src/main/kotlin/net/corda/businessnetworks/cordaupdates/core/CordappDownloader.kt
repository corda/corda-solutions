package net.corda.businessnetworks.cordaupdates.core


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
import org.eclipse.aether.resolution.VersionRangeRequest
import org.eclipse.aether.resolution.VersionRangeResult
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transport.file.FileTransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory
import java.lang.Exception

class CordappDownloader(remoteRepoUrl : String, val localRepoPath : String) {
    private val repositorySystem : RepositorySystem by lazy {
        val locator = MavenRepositorySystemUtils.newServiceLocator()
        locator.addService<RepositoryConnectorFactory>(RepositoryConnectorFactory::class.java, BasicRepositoryConnectorFactory::class.java)
        locator.addService<TransporterFactory>(TransporterFactory::class.java, FileTransporterFactory::class.java)
        locator.addService<TransporterFactory>(TransporterFactory::class.java, HttpTransporterFactory::class.java)
        locator.getService(RepositorySystem::class.java)
    }

    private val remoteRepository : RemoteRepository by lazy {
        RemoteRepository.Builder("remote", "default", remoteRepoUrl).build()
    }

    fun listVersions(rangeRequest : String) : VersionRangeResult {
        val versionRangeRequest = VersionRangeRequest(DefaultArtifact(rangeRequest),
                listOf(remoteRepository), null)

        val session = newSession()
        session.localRepositoryManager = SimpleLocalRepositoryManagerFactory().newInstance(session, LocalRepository(localRepoPath))

        return repositorySystem.resolveVersionRange(session, versionRangeRequest) ?: throw CordaUpdatesException("Unable to resolve versions range for $rangeRequest")
    }

    fun downloadVersionRange(rangeRequest : String) {

    }

    fun downloadVersion(mavenCoords : String) {
        val session = newSession()
        session.localRepositoryManager = SimpleLocalRepositoryManagerFactory().newInstance(session, LocalRepository(localRepoPath))

        val dependency = Dependency(DefaultArtifact(mavenCoords), "compile")

        val collectRequest = CollectRequest()
        collectRequest.root = dependency
        collectRequest.addRepository(remoteRepository)
        val node = repositorySystem.collectDependencies(session, collectRequest).root

        val dependencyRequest = DependencyRequest()
        dependencyRequest.root = node

        repositorySystem.resolveDependencies(session, dependencyRequest)
    }
}

class CordaUpdatesException(message : String) : Exception(message)