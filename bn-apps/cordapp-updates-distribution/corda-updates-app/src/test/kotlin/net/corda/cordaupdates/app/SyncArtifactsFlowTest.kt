package net.corda.cordaupdates.app

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.cordaupdates.core.ArtifactMetadata
import net.corda.businessnetworks.cordaupdates.core.SyncerConfiguration
import net.corda.businessnetworks.cordaupdates.core.CordappSource
import net.corda.businessnetworks.cordaupdates.core.VersionMetadata
import net.corda.businessnetworks.cordaupdates.testutils.RepoVerifier
import net.corda.cordaupdates.app.member.ArtifactsMetadataCache
import net.corda.cordaupdates.app.member.GetLastSyncTimeFlow
import net.corda.cordaupdates.app.member.ScheduleSyncFlow
import net.corda.cordaupdates.app.member.SyncArtifactsFlow
import net.corda.cordaupdates.app.states.ScheduledSyncContract
import net.corda.cordaupdates.app.states.ScheduledSyncState
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SyncArtifactsFlowTest {
    private lateinit var mockNetwork : MockNetwork
    private lateinit var node : StartedMockNode
    private lateinit var localRepoPath : Path
    private lateinit var repoVerifier : RepoVerifier
    private lateinit var syncerConfig : SyncerConfiguration

    @Before
    fun setup() {
        localRepoPath = Files.createTempDirectory("FakeRepo")
        repoVerifier = RepoVerifier(localRepoPath.toString())
        syncerConfig = SyncerConfiguration(localRepoPath = localRepoPath.toString(),
                cordappSources = listOf(CordappSource("file:../TestRepo",
                        cordapps = listOf("net.example:test-artifact", "net.example:test-artifact-2"))))

        val participantName = CordaX500Name("Participant", "New York", "US")
        val notaryName = CordaX500Name.parse("O=Notary,L=London,C=GB")

        mockNetwork = MockNetwork(cordappPackages = listOf("net.corda.cordaupdates.app", "net.corda.cordaupdates.states"),
                notarySpecs = listOf(MockNetworkNotarySpec(notaryName)))
        node = mockNetwork.createPartyNode(participantName)

        node.startFlow(ReloadMemberConfigurationFlow("corda-updates-app.conf")).getOrThrow()
    }

    @After
    fun tearDown() {
        localRepoPath.toFile().deleteRecursively()
        mockNetwork.stopNodes()
    }

    @Test
    fun `syncWithRemoteRepositoryFlow happy path`() {
        val future = node.startFlow(SyncArtifactsFlow(syncerConfig, false))
        mockNetwork.runNetwork()
        val artifacts = future.getOrThrow()!!

        // verify local repo contents
        repoVerifier.shouldContain("net:example", "test-artifact", setOf("0.1", "0.5", "1.0", "1.5", "2.0"))
                .shouldContain("net:example", "test-artifact-2", setOf("1.0", "2.0"))
                .verify()

        // verify returned metadata
        assertEquals(
                setOf(ArtifactMetadata("net.example", "test-artifact", versions = listOf("0.1", "0.5", "1.0", "1.5", "2.0").map { VersionMetadata(it, false) }),
                        ArtifactMetadata("net.example", "test-artifact-2", versions = listOf("1.0", "2.0").map { VersionMetadata(it, false) })),
                artifacts.toSet()
        )

        // make sure that metadata holder service has been updated
        val dataFromServiceFuture = node.startFlow(GetDataFromSyncerServiceFlow())
        mockNetwork.runNetwork()
        val dataFromService = dataFromServiceFuture.getOrThrow()
        assertEquals(artifacts.toSet(), dataFromService.toSet())
    }

    @Test
    fun `if ScheduleSyncStates exist on the ledger - spend them and issue a new one`() {
        // issue some states onto the ledger
        val issueSomeStatesFuture = node.startFlow(IssueSomeScheduledStatesFlow(1000000L, 5))
        mockNetwork.runNetwork()
        issueSomeStatesFuture.getOrThrow()

        val scheduleSyncFuture = node.startFlow(ScheduleSyncFlow(syncerConfig, false))
        mockNetwork.runNetwork()
        scheduleSyncFuture.getOrThrow()

        // should contain a single sync state on the ledger
        verifyScheduledState()
    }

    @Test
    fun `should update last sync time after artifacts have been synced`() {
        // last sync time should be null initially
        assertNull(getLastSyncTime())

        val future = node.startFlow(SyncArtifactsFlow(syncerConfig, false))
        mockNetwork.runNetwork()
        future.getOrThrow()!!

        assertNotNull(getLastSyncTime())
    }

    private fun getLastSyncTime() : Instant? {
        val lastSyncTimeFuture = node.startFlow(GetLastSyncTimeFlow())
        mockNetwork.runNetwork()
        return lastSyncTimeFuture.getOrThrow()
    }


    private fun verifyScheduledState() {
        val vaultState = node.transaction { node.services.vaultService.queryBy<ScheduledSyncState>().states.single().state.data }
        assertEquals(9999999L, vaultState.syncInterval)
        assertEquals(node.services.myInfo.legalIdentities.single(), vaultState.owner)
    }
}

// Flow to issue some random ScheduledSyncState onto the ledger
class IssueSomeScheduledStatesFlow(val syncInterval : Long, val statesQty : Int) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        (1..statesQty).forEach { _ ->
            val notary = serviceHub.networkMapCache.notaryIdentities.single()
            val builder = TransactionBuilder(notary)
                    .addOutputState(ScheduledSyncState(syncInterval, ourIdentity), ScheduledSyncContract.CONTRACT_NAME)
                    .addCommand(ScheduledSyncContract.Commands.Start(), ourIdentity.owningKey)
            builder.verify(serviceHub)
            val signedTx = serviceHub.signInitialTransaction(builder)
            subFlow(FinalityFlow(signedTx))
        }
    }
}


class GetDataFromSyncerServiceFlow : FlowLogic<List<ArtifactMetadata>>() {
    @Suspendable
    override fun call() : List<ArtifactMetadata> {
        val service = serviceHub.cordaService(ArtifactsMetadataCache::class.java)
        return service.cache
    }
}