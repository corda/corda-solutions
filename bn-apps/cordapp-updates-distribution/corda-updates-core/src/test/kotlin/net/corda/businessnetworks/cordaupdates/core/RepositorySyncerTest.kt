package net.corda.businessnetworks.cordaupdates.core

import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepositorySyncerTest {
    private lateinit var localRepoPath : Path

    @Before
    fun setup() {
        localRepoPath = Files.createTempDirectory("FakeLocalRepo")
    }

    @After
    fun cleanUp() {
        localRepoPath.toFile().deleteRecursively()
    }

    @Test
    fun happyPath() {
        val syncConfiguration = SyncerConfiguration(
                localRepoPath = localRepoPath.toAbsolutePath().toString(),
                tasks = listOf(SyncerTask(remoteRepoUrl = "file:${File("../TestRepo").canonicalPath}",
                                artifacts = listOf("net.example:test-artifact", "net.example:test-artifact-3"))))
        val syncer = RepositorySyncer(syncConfiguration)

        syncer.sync()

        assertTrue(localRepoPath.resolve("net/example/test-artifact/0.1/test-artifact-0.1.jar").toFile()!!.exists())
        assertTrue(localRepoPath.resolve("net/example/test-artifact/0.5/test-artifact-0.5.jar").toFile()!!.exists())
        assertTrue(localRepoPath.resolve("net/example/test-artifact/1.0/test-artifact-1.0.jar").toFile()!!.exists())
        assertTrue(localRepoPath.resolve("net/example/test-artifact/1.5/test-artifact-1.5.jar").toFile()!!.exists())
        assertTrue(localRepoPath.resolve("net/example/test-artifact/2.0/test-artifact-2.0.jar").toFile()!!.exists())
        assertTrue(localRepoPath.resolve("net/example/test-artifact-3/0.1/test-artifact-3-0.1.jar").toFile()!!.exists())
        assertTrue(localRepoPath.resolve("net/example/test-artifact-3/1.5/test-artifact-3-1.5.jar").toFile()!!.exists())
        // test-artifact-2 should not have been synced
        assertFalse(localRepoPath.resolve("net/example/test-artifact-2/").toFile()!!.exists())
    }

    @Test
    fun testConfigurationFormat() {
        SyncerConfiguration.readFromFile(File(RepositorySyncerTest::class.java.classLoader.getResource("test-syncer-configuration.yaml").file))
    }
}