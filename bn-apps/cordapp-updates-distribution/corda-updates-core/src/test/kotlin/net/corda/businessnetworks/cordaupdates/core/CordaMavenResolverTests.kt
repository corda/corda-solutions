package net.corda.businessnetworks.cordaupdates.core

import net.corda.businessnetworks.cordaupdates.testutils.RepoVerifier
import org.eclipse.aether.resolution.ArtifactResolutionException
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals

class CordaMavenResolverTests {
    private lateinit var resolver : CordaMavenResolver
    private lateinit var localRepoPath : Path
    private lateinit var repoVerifier : RepoVerifier

    @Before
    fun setup() {
        localRepoPath = Files.createTempDirectory("FakeLocalRepo")
        resolver = CordaMavenResolver.create(
                remoteRepoUrl = "file:${File("../TestRepo").canonicalPath}",
                localRepoPath = localRepoPath.toAbsolutePath().toString())
        repoVerifier = RepoVerifier(localRepoPath.toString())
    }

    @Test
    fun shutDown() {
        localRepoPath.toFile().deleteRecursively()
    }

    @Test
    fun resolveVersionRange() {
        val result = resolver.resolveVersionRange("net.example:test-artifact:[0,)")
        assertEquals(ArtifactMetadata("net.example", "test-artifact", versions = listOf("0.1", "0.5", "1.0", "1.5", "2.0")), result)
    }

    @Test
    fun `resolveVersionRange returns an empty version list for not existing artifact` () {
        val result = resolver.resolveVersionRange("net.example:does-not-exist:[0.2,1.7]")
        assertEquals(ArtifactMetadata("net.example", "does-not-exist"), result)
    }

    @Test
    fun `downloadArtifact happy path`() {
        val result = resolver.downloadVersion("net.example:test-artifact:0.5")
        assertEquals(ArtifactMetadata("net.example", "test-artifact", versions = listOf("0.5")), result)
        repoVerifier.shouldContain("net:example", "test-artifact", setOf("0.5")).verify()
    }

    @Test(expected = ArtifactResolutionException::class)
    fun `downloadArtifact throws DependencyResolutionException if artifact was not found`() {
        resolver.downloadVersion("net.example:does-not-exist:0.5")
    }

    @Test
    fun downloadVersionRange() {
        val result = resolver.downloadVersionRange("net.example:test-artifact:[0.2,1.7]")
        assertEquals(ArtifactMetadata("net.example", "test-artifact", versions = listOf("0.5", "1.0", "1.5")), result)
        repoVerifier.shouldContain("net:example", "test-artifact", setOf("0.5", "1.0", "1.5")).verify()
    }

    @Test
    fun `downloadVersionRange returns an empty version list for not existing artifact`() {
        val result = resolver.downloadVersionRange("net.example:does-not-exist:[0.2,1.7]")
        assertEquals(ArtifactMetadata("net.example", "does-not-exist"), result)
    }
}