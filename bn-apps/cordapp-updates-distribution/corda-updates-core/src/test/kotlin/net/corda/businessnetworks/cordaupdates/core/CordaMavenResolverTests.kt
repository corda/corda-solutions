package net.corda.businessnetworks.cordaupdates.core

import org.eclipse.aether.resolution.VersionRangeResult
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CordaMavenResolverTests {
    companion object {
        val LOCAL_REPO_PATH_PREFIX = "TestLocalRepo"
    }

    lateinit var resolver : CordaMavenResolver
    lateinit var localRepoPath : Path

    @Before
    fun setup() {
        localRepoPath = Files.createTempDirectory(LOCAL_REPO_PATH_PREFIX)
        resolver = CordaMavenResolver("file://${CordaMavenResolverTests::class.java.classLoader.getResource("TestRepo").file!!}",
                localRepoPath.toAbsolutePath().toString())
    }

    @Test
    fun shutDown() {
        localRepoPath.toFile().deleteOnExit()
    }

    @Test
    fun peekVersion() {
        assertTrue(resolver.peekVersion("net.example:test-artifact:1.5"))
        assertFalse(resolver.peekVersion("net.example:not-existing:0.1"))
    }

    @Test
    fun resolveVersionRange() {
        assertEquals(setOf("0.1", "0.5", "1.0", "1.5", "2.0"), versionSet(resolver.resolveVersionRange("net.example:test-artifact:[0,)")))
        assertEquals(setOf("0.5", "1.0", "1.5"), versionSet(resolver.resolveVersionRange("net.example:test-artifact:[0.2,1.7]")))
        assertTrue(resolver.resolveVersionRange("net.example:not-existing:[0.2,1.7]").versions.isEmpty())
    }

    @Test
    fun downloadArtifact() {
        resolver.downloadVersion("net.example:test-artifact:0.5")
        assertTrue(localRepoPath.resolve("net/example/test-artifact/0.5/test-artifact-0.5.jar").toFile()!!.exists())
    }

    @Test
    fun downloadVersionRange() {
        resolver.downloadVersionRange("net.example:test-artifact:[0.2,1.7]")
        assertTrue(localRepoPath.resolve("net/example/test-artifact/0.5/test-artifact-0.5.jar").toFile()!!.exists())
        assertTrue(localRepoPath.resolve("net/example/test-artifact/1.0/test-artifact-1.0.jar").toFile()!!.exists())
        assertTrue(localRepoPath.resolve("net/example/test-artifact/1.5/test-artifact-1.5.jar").toFile()!!.exists())
        assertFalse(localRepoPath.resolve("net/example/test-artifact/0.1/test-artifact-0.1.jar").toFile()!!.exists())
        assertFalse(localRepoPath.resolve("net/example/test-artifact/2.0/test-artifact-2.0.jar").toFile()!!.exists())
    }

    fun versionSet(rangeResult : VersionRangeResult) = rangeResult.versions!!.asSequence().map { it.toString() }.toSet()
}
