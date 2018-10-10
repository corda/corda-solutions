package net.corda.cordaupdates.app

import net.corda.businessnetworks.cordaupdates.core.ArtifactMetadata
import net.corda.businessnetworks.cordaupdates.core.SyncerConfiguration
import net.corda.businessnetworks.cordaupdates.core.CordappSource
import net.corda.businessnetworks.cordaupdates.core.VersionMetadata
import net.corda.cordaupdates.app.member.GetAvailableVersionsFlow
import net.corda.cordaupdates.app.member.SyncArtifactsFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GetAvailableVersionsFlowTest {
    private lateinit var mockNetwork : MockNetwork
    private lateinit var node : StartedMockNode
    private lateinit var localRepoPath : Path
    private lateinit var syncerConfig : SyncerConfiguration

    @Before
    fun setup() {
        localRepoPath = Files.createTempDirectory("FakeRepo")
        syncerConfig = SyncerConfiguration(localRepoPath = localRepoPath.toString(),
                cordappSources = listOf(CordappSource("file:../TestRepo",
                        cordapps = listOf("net.example:test-artifact", "net.example:test-artifact-2"))))

        val participantName = CordaX500Name("Participant", "New York", "US")
        val notaryName = CordaX500Name.parse("O=Notary,L=London,C=GB")

        mockNetwork = MockNetwork(cordappPackages = listOf("net.corda.cordaupdates.app", "net.corda.cordaupdates.states"),
                notarySpecs = listOf(MockNetworkNotarySpec(notaryName)))
        node = mockNetwork.createPartyNode(participantName)
    }

    @After
    fun tearDown() {
        localRepoPath.toFile().deleteRecursively()
        mockNetwork.stopNodes()
    }

    private fun syncArtifacts() {
        // syncing artifacts
        val future = node.startFlow(SyncArtifactsFlow(syncerConfig, false))
        mockNetwork.runNetwork()
        future.getOrThrow()!!
    }

    private fun getAvailableVersions() : ArtifactMetadata? {
        val future = node.startFlow(GetAvailableVersionsFlow("net.example:test-artifact"))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    @Test
    fun testHappyPath() {
        // no versions should exist before synchronization was done
        assertNull(getAvailableVersions())

        syncArtifacts()

        val artifact = getAvailableVersions()!!

        // verify returned metadata
        assertEquals(
                ArtifactMetadata("net.example", "test-artifact", versions = listOf("0.1", "0.5", "1.0", "1.5", "2.0").map { VersionMetadata(it, false) }),
                artifact
        )
    }
}
