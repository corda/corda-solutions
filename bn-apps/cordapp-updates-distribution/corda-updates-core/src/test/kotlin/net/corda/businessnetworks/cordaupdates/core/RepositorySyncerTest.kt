package net.corda.businessnetworks.cordaupdates.core

import net.corda.businessnetworks.cordaupdates.testutils.RepoVerifier
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class RepositorySyncerTest {
    private lateinit var localRepoPath : Path
    private lateinit var repoVerifier : RepoVerifier

    @Before
    fun setup() {
        localRepoPath = Files.createTempDirectory("FakeLocalRepo")
        repoVerifier = RepoVerifier(localRepoPath.toString())
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

        repoVerifier
                .shouldContain("net:example", "test-artifact", setOf("0.1", "0.5", "1.0", "1.5", "2.0"))
                .shouldContain("net:example", "test-artifact-3", setOf("0.1", "1.5"))
                .verify()
    }

    @Test
    fun testConfigurationFormat() {
        SyncerConfiguration.readFromFile(File(RepositorySyncerTest::class.java.classLoader.getResource("test-syncer-configuration.yaml").file))
    }
}