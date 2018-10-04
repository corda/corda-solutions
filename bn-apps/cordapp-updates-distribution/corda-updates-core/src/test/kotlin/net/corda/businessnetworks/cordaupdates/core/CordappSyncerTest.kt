package net.corda.businessnetworks.cordaupdates.core

import net.corda.businessnetworks.cordaupdates.testutils.RepoVerifier
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class CordappSyncerTest {
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
                cordappSources = listOf(CordappSource(remoteRepoUrl = "file:${File("../TestRepo").canonicalPath}",
                    cordapps = listOf("net.example:test-artifact", "net.example:test-artifact-3"))))
        val syncer = CordappSyncer(syncConfiguration)

        syncer.syncCordapps()

        repoVerifier
                .shouldContain("net:example", "test-artifact", setOf("0.1", "0.5", "1.0", "1.5", "2.0"))
                .shouldContain("net:example", "test-artifact-3", setOf("0.1", "1.5"))
                .verify()
    }

    @Test
    fun testConfigurationFormat() {
        SyncerConfiguration.readFromFile(File(CordappSyncerTest::class.java.classLoader.getResource("test-syncer-configuration.yaml").file))
    }
}