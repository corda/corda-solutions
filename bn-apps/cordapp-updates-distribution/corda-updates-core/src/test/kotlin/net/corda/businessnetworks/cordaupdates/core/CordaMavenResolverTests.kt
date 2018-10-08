package net.corda.businessnetworks.cordaupdates.core

import net.corda.businessnetworks.cordaupdates.testutils.RepoVerifier
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
        assertEquals(ArtifactMetadata("net.example", "test-artifact", versions = listOf("0.1", "0.5", "1.0", "1.5", "2.0").map { VersionMetadata(it, false) }), result)
    }

    @Test
    fun `resolveVersionRange returns an empty version list for not existing artifact` () {
        val result = resolver.resolveVersionRange("net.example:does-not-exist:[0.2,1.7]")
        assertEquals(ArtifactMetadata("net.example", "does-not-exist"), result)
    }

    @Test
    fun `resolveVersionRange returns an empty list if can't reach the repository` () {
        resolver = CordaMavenResolver.create(
                httpProxyType = "http",
                httpProxyHost = "localhost",
                httpProxyPort = 10,
                remoteRepoUrl = "https://repo.maven.apache.org/maven2/",
                localRepoPath = localRepoPath.toAbsolutePath().toString())
        resolver.resolveVersionRange("net.example:does-not-exist:[0.2,1.7]")
    }

    @Test
    fun `downloadArtifact happy path`() {
        val result = resolver.downloadVersion("net.example:test-artifact:0.5")
        assertEquals(ArtifactMetadata("net.example", "test-artifact", versions = listOf(VersionMetadata("0.5", false))), result)
        repoVerifier.shouldContain("net:example", "test-artifact", setOf("0.5")).verify()
    }

    @Test(expected = ResourceTransferException::class)
    fun `downloadArtifact should throw ArtifactResolutionException if artifact can't be transferred`() {
        resolver = CordaMavenResolver.create(
                httpProxyType = "http",
                httpProxyHost = "localhost",
                httpProxyPort = 10,
                remoteRepoUrl = "https://repo.maven.apache.org/maven2/",
                localRepoPath = localRepoPath.toAbsolutePath().toString())

        resolver.downloadVersion("net.example:test-artifact:0.5")
    }

    @Test(expected = ResourceNotFoundException::class)
    fun `downloadArtifact throws ArtifactResolutionException if artifact was not found`() {
        resolver.downloadVersion("net.example:does-not-exist:0.5")
    }


    @Test
    fun `downloadArtifact isFromLocal should be true if an artifact was resolved from the local repo`() {
        resolver.downloadVersion("net.example:test-artifact:0.5")
        val result = resolver.downloadVersion("net.example:test-artifact:0.5")
        assertEquals(ArtifactMetadata("net.example", "test-artifact", versions = listOf(VersionMetadata("0.5", true))), result)
    }

    @Test
    fun downloadVersionRange() {
        val result = resolver.downloadVersionRange("net.example:test-artifact:[0.2,1.7]")
        assertEquals(ArtifactMetadata("net.example", "test-artifact", versions = listOf("0.5", "1.0", "1.5").map { VersionMetadata(it, false) }), result)
        repoVerifier.shouldContain("net:example", "test-artifact", setOf("0.5", "1.0", "1.5")).verify()
    }

    @Test
    fun `downloadVersionRange returns an empty version list for not existing artifact`() {
        val result = resolver.downloadVersionRange("net.example:does-not-exist:[0.2,1.7]")
        assertEquals(ArtifactMetadata("net.example", "does-not-exist"), result)
    }

    @Test
    fun `downloadVersionRange should indicate if a version has been taken from the local repo`() {
        resolver.downloadVersion("net.example:test-artifact:1.0")
        val result = resolver.downloadVersionRange("net.example:test-artifact:[1.0,1.7]")
        assertEquals(ArtifactMetadata("net.example", "test-artifact", versions = listOf(VersionMetadata("1.0", true), VersionMetadata("1.5", false))), result)
    }
}