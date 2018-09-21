package net.corda.businessnetworks.cordaupdates.core

import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CordappDownloaderTests {
    companion object {
        val LOCAL_REPO_PATH_PREFIX = "TestLocalRepo"
    }

    lateinit var downloader : CordappDownloader
    lateinit var localRepoPath : Path

    @Before
    fun setup() {
        localRepoPath = Files.createTempDirectory(LOCAL_REPO_PATH_PREFIX)
        downloader = CordappDownloader("file://${CordappDownloaderTests::class.java.classLoader.getResource("TestRepo").file!!}",
                localRepoPath.toAbsolutePath().toString())
    }

    @Test
    fun shutDown() {
        localRepoPath.toFile().deleteOnExit()
    }

    @Test
    fun listVersionRanges() {
        val versions1 = downloader.listVersions("net.example:test-artifact:[0,)")
        assertEquals(versions1.versions!!.map { it.toString() }.toSet(), setOf("0.1", "0.5", "1.0", "1.5", "2.0"))

        val versions2 = downloader.listVersions("net.example:test-artifact:[0.2,1.7]")
        assertEquals(versions2.versions!!.map { it.toString() }.toSet(), setOf("0.5", "1.0", "1.5"))
    }

    @Test
    fun downloadArtifact() {
        downloader.downloadVersion("net.example:test-artifact:0.5")
        assertTrue(localRepoPath.resolve("net/example/test-artifact/0.5/test-artifact-0.5.jar").toFile()!!.exists())
    }

    @Test
    fun downloadVersionRange() {
        downloader.downloadVersionRange("net.example:test-artifact:[0.2,1.7]")
        assertTrue(localRepoPath.resolve("net/example/test-artifact/0.5/test-artifact-0.5.jar").toFile()!!.exists())
        assertTrue(localRepoPath.resolve("net/example/test-artifact/1.0/test-artifact-1.0.jar").toFile()!!.exists())
        assertTrue(localRepoPath.resolve("net/example/test-artifact/1.5/test-artifact-1.5.jar").toFile()!!.exists())
        assertFalse(localRepoPath.resolve("net/example/test-artifact/0.1/test-artifact-0.1.jar").toFile()!!.exists())
        assertFalse(localRepoPath.resolve("net/example/test-artifact/2.0/test-artifact-2.0.jar").toFile()!!.exists())
    }
}
