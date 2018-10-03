package net.corda.cordaupdates.app

import net.corda.businessnetworks.cordaupdates.core.ArtifactMetadata
import net.corda.businessnetworks.cordaupdates.core.SyncerConfiguration
import net.corda.businessnetworks.cordaupdates.core.SyncerTask
import net.corda.cordaupdates.app.member.GetAvailableVersionsFlow
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

class GetAvailableVersionsFlowTest {
    private lateinit var mockNetwork : MockNetwork
    private lateinit var node : StartedMockNode
    private lateinit var localRepoPath : Path
    private lateinit var syncerConfig : SyncerConfiguration

    @Before
    fun setup() {
        localRepoPath = Files.createTempDirectory("FakeRepo")
        syncerConfig = SyncerConfiguration(localRepoPath = localRepoPath.toString(),
                tasks = listOf(SyncerTask("file:../TestRepo",
                        artifacts = listOf("net.example:test-artifact", "net.example:test-artifact-2"))))

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

    @Test
    fun testHappyPath() {
        val future = node.startFlow(GetAvailableVersionsFlow(syncerConfig, false))
        mockNetwork.runNetwork()
        val artifacts = future.getOrThrow()!!

        // verify returned metadata
        assertEquals(
                setOf(ArtifactMetadata("net.example", "test-artifact", versions = listOf("0.1", "0.5", "1.0", "1.5", "2.0")),
                        ArtifactMetadata("net.example", "test-artifact-2", versions = listOf("1.0", "2.0"))),
                artifacts.toSet()
        )

        // make sure that metadata holder service has been updated
        val dataFromServiceFuture = node.startFlow(GetDataFromSyncerServiceFlow())
        mockNetwork.runNetwork()
        val dataFromService = dataFromServiceFuture.getOrThrow()
        assertEquals(artifacts.toSet(), dataFromService.toSet())
    }
}
