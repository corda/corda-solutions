package net.corda.cordaupdates.app

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.cordaupdates.core.SyncerConfiguration
import net.corda.businessnetworks.cordaupdates.core.SyncerTask
import net.corda.businessnetworks.cordaupdates.testutils.RepoVerifier
import net.corda.client.rpc.CordaRPCClient
import net.corda.cordaupdates.app.states.ScheduledSyncContract
import net.corda.cordaupdates.app.states.ScheduledSyncState
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.User
import org.junit.Test
import java.lang.Thread.sleep
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals

class SyncWithRemoteRepositoryFlowTest {
    private lateinit var node: NodeHandle
    private lateinit var localRepoPath : Path
    private lateinit var repoVerifier : RepoVerifier
    private lateinit var syncerConfig : SyncerConfiguration


    private fun genericTest(testFunction : (rpc : CordaRPCOps) -> Unit) {
        val user1 = User("test", "test", permissions = setOf("ALL"))
        val participantName = CordaX500Name("Participant","New York","US")
        val notaryName = CordaX500Name.parse("O=Notary,L=London,C=GB")

        driver(DriverParameters(
                extraCordappPackagesToScan = listOf("net.corda.cordaupdates.app"),
                startNodesInProcess = true,
                notarySpecs = listOf(NotarySpec(notaryName, false)))) {

            node = startNode(NodeParameters(providedName = participantName), rpcUsers = listOf(user1)).getOrThrow()
            localRepoPath = Files.createTempDirectory("FakeRepo")
            repoVerifier = RepoVerifier(localRepoPath.toString())
            syncerConfig = SyncerConfiguration(localRepoPath = localRepoPath.toString(),
                    tasks = listOf(SyncerTask("file:../TestRepo",
                            artifacts = listOf("net.example:test-artifact", "net.example:test-artifact-2"))))

            val rpcClient = CordaRPCClient(node.rpcAddress)
            val rpcProxy: CordaRPCOps = rpcClient.start("test", "test").proxy

            try {
                testFunction(rpcProxy)
            } finally {
                localRepoPath.toFile().deleteRecursively()
            }
            node.stop()
        }
    }

    @Test
    fun happyPath() {
        genericTest {rpc ->

            val future = rpc.startFlowDynamic(ScheduleSyncFlow::class.java, syncerConfig).returnValue
            future.getOrThrow()

            // repo sync is done asynchronously, give it some time to finish
            sleep(5000)

            // Repo should be in sync with remote
            repoVerifier.shouldContain("net:example", "test-artifact", setOf("0.1", "0.5", "1.0", "1.5", "2.0"))
                    .shouldContain("net:example", "test-artifact-2", setOf("1.0", "2.0"))
                    .verify()

            // should issue a scheduled state on the ledger
            verifySyncState(rpc)
        }
    }

    @Test
    fun `if multiple of ScheduleSyncStates exist on the ledger - spend them and issue a new one`() {
        genericTest {rpc ->
            // issue some states onto the ledger
            val issueSomeStatesFuture = rpc.startFlowDynamic(IssueSomeScheduledStatesFlow::class.java).returnValue
            issueSomeStatesFuture.getOrThrow()

            val scheduleSyncFuture = rpc.startFlowDynamic(ScheduleSyncFlow::class.java,syncerConfig).returnValue
            scheduleSyncFuture.getOrThrow()

            // should contain a single sync state on the ledger
            verifySyncState(rpc)
        }
    }

    private fun verifySyncState(rpc : CordaRPCOps) {
        val syncState = rpc.vaultQueryBy<ScheduledSyncState>().states.single().state.data
        assertEquals(9999999L, syncState.syncInterval)
        assertEquals(rpc.nodeInfo().legalIdentities.single(), syncState.owner)
    }
}

// Flow to issue some random ScheduledSyncState onto the ledger
@StartableByRPC
class IssueSomeScheduledStatesFlow : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        (1..5).forEach { _ ->
            val notary = serviceHub.networkMapCache.notaryIdentities.single()
            val builder = TransactionBuilder(notary)
                    .addOutputState(ScheduledSyncState(10000000L, ourIdentity), ScheduledSyncContract.CONTRACT_NAME)
                    .addCommand(ScheduledSyncContract.Commands.Start(), ourIdentity.owningKey)
            builder.verify(serviceHub)
            val signedTx = serviceHub.signInitialTransaction(builder)
            subFlow(FinalityFlow(signedTx))
        }
    }
}