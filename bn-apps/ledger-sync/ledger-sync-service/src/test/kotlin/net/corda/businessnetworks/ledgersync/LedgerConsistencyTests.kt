package net.corda.businessnetworks.ledgersync

import net.corda.businessnetworks.membership.bno.ActivateMembershipFlow
import net.corda.businessnetworks.membership.member.GetMembershipsFlow
import net.corda.businessnetworks.membership.member.RequestMembershipFlow
import net.corda.businessnetworks.membership.states.Membership
import net.corda.businessnetworks.membership.states.Membership.State
import net.corda.businessnetworks.membership.states.MembershipMetadata
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.services.Vault.Page
import net.corda.core.node.services.Vault.StateStatus.ALL
import net.corda.core.node.services.vault.MAX_PAGE_SIZE
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria.VaultQueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.User
import org.junit.Test
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Suppress("PrivatePropertyName")
class LedgerConsistencyTests {
    private lateinit var bnoNode: NodeHandle
    private lateinit var participantsNodes: List<NodeHandle>

    private val MEMBERSHIP_DATA = MembershipMetadata("DEFAULT")

    private fun onNetwork(function: () -> Unit) {
        val user = User("user1", "test", permissions = setOf("ALL"))

        driver(DriverParameters(
                isDebug = false,
                extraCordappPackagesToScan = listOf(
                        "net.corda.businessnetworks.membership",
                        "net.corda.businessnetworks.membership.states",
                        "net.corda.businessnetworks.ledgersync"
                ),
                notarySpecs = listOf(NotarySpec(CordaX500Name("Notary", "London", "GB"), true)),
                waitForAllNodesToFinish = false,
                startNodesInProcess = true
        )) {
            val bno = NodeParameters(providedName = CordaX500Name("BNO", "New York", "US"))

            val members = listOf(
                    NodeParameters(providedName = CordaX500Name("Member 1", "Paris", "FR")),
                    NodeParameters(providedName = CordaX500Name("Member 2", "Paris", "FR")),
                    NodeParameters(providedName = CordaX500Name("Member 3", "Paris", "FR"))
            )

            val nodes = (listOf(bno) + members).map {
                startNode(it, rpcUsers = listOf(user))
            }.map {
                it.toCompletableFuture()
            }.toTypedArray()

            // await completion
            CompletableFuture.allOf(*nodes).get()

            nodes.map { it.join() }.let { started ->
                bnoNode = started.first()
                participantsNodes = started - bnoNode
            }

            participantsNodes.forEach {
                it.elevateToMember()
            }

            // after initialising the network
            function()
        }
    }

    @Test
    fun `members receive no ids to sync if they hold all transactions the counterparty is aware of`() {
        onNetwork {
            val requester = participantsNodes[0]

            assertEquals(0, requester.bogusStateCount())

            requester.createTransactions()

            assertEquals(2, requester.bogusStateCount())

            val missingTransactions = requester.runRequestLedgerSyncFlow(requester.members())

            assertEquals(mapOf(
                    participantsNodes[1].identity() to LedgerSyncFindings(emptyList(), emptyList()),
                    participantsNodes[2].identity() to LedgerSyncFindings(emptyList(), emptyList())
            ), missingTransactions)
        }

    }

    @Test
    fun `reports member ids to sync missing from requester`() {
        onNetwork {
            val requester = participantsNodes[0]

            requester.createTransactions()
            assertEquals(2, requester.bogusStateCount())
            requester.simulateCatastrophicFailure()
            assertEquals(0, requester.bogusStateCount())
            requester.runRequestLedgerSyncFlow(requester.members())

            val missingTransactions = requester.runRequestLedgerSyncFlow(requester.members())

            assertEquals(1, missingTransactions[participantsNodes[1].identity()]!!.missingAtRequester.size)
            assertEquals(1, missingTransactions[participantsNodes[2].identity()]!!.missingAtRequester.size)
        }
    }

    @Test
    fun `reports member ids to sync from requestee`() {
        onNetwork {
            val requester = participantsNodes[0]

            requester.createTransactions()
            participantsNodes[1].simulateCatastrophicFailure()
            assertEquals(0, participantsNodes[1].bogusStateCount())

            participantsNodes[2].simulateCatastrophicFailure()
            assertEquals(0, participantsNodes[2].bogusStateCount())

            requester.runRequestLedgerSyncFlow(requester.members())

            val missingTransactions = requester.runRequestLedgerSyncFlow(requester.members())

            assertEquals(1, missingTransactions[participantsNodes[1].identity()]!!.missingAtRequestee.size)
            assertEquals(0, missingTransactions[participantsNodes[1].identity()]!!.missingAtRequester.size)
            assertEquals(1, missingTransactions[participantsNodes[2].identity()]!!.missingAtRequestee.size)
            assertEquals(0, missingTransactions[participantsNodes[2].identity()]!!.missingAtRequester.size)
        }
    }

    @Test
    fun `ledger consistency is reported for consistent ledgers`() {
        onNetwork {
            val requester = participantsNodes[0]
            requester.createTransactions()
            val actual = requester.runEvaluateLedgerConsistencyFlow(requester.members())
            assertEquals(mapOf(
                    participantsNodes[1].identity() to true,
                    participantsNodes[2].identity() to true
            ), actual)
        }
    }

    @Test
    fun `ledger consistency is reported for inconsistent ledger`() {
        onNetwork {
            val requester = participantsNodes[0]
            requester.createTransactions()
            requester.simulateCatastrophicFailure()
            val actual = requester.runEvaluateLedgerConsistencyFlow(requester.members())
            assertEquals(mapOf(
                    participantsNodes[1].identity() to false,
                    participantsNodes[2].identity() to false
            ), actual)
        }
    }

    @Test
    fun `ledger consistency is reported for inconsistent counterparties`() {
        onNetwork {
            val requester = participantsNodes[0]
            requester.createTransactions()
            participantsNodes[2].simulateCatastrophicFailure()
            val actual = requester.runEvaluateLedgerConsistencyFlow(requester.members())
            assertEquals(mapOf(
                    participantsNodes[1].identity() to true,
                    participantsNodes[2].identity() to false
            ), actual)
        }
    }

    @Test
    fun `transactions can be recovered`() {
        onNetwork {
            val requester = participantsNodes[0]
            requester.createTransactions(3)

            assertEquals(6, requester.bogusStateCount())

            requester.simulateCatastrophicFailure()

            val consistencyResult = requester.runEvaluateLedgerConsistencyFlow(requester.members())

            assertEquals(mapOf(
                    participantsNodes[1].identity() to false,
                    participantsNodes[2].identity() to false
            ), consistencyResult)

            assertEquals(0, requester.bogusStateCount())

            val ledgerSyncResult = requester.runRequestLedgerSyncFlow(requester.members())

            assertEquals(3, ledgerSyncResult[participantsNodes[1].identity()]!!.missingAtRequester.size)
            assertEquals(3, ledgerSyncResult[participantsNodes[2].identity()]!!.missingAtRequester.size)

            requester.runTransactionRecoveryFlow(ledgerSyncResult)

            assertEquals(6, requester.bogusStateCount())
        }
    }

    private fun NodeHandle.elevateToMember() {
        runRequestMembershipFlow()
        Thread.sleep(1000)
        val membership = rpc.vaultQueryBy<State>().states.single()
        bnoNode.runActivateMembershipFlow(membership)
    }

    private fun NodeHandle.runRequestMembershipFlow() {
        val future = rpc.startFlowDynamic(RequestMembershipFlow::class.java, MEMBERSHIP_DATA)
        future.returnValue.getOrThrow()
    }

    private fun NodeHandle.runActivateMembershipFlow(membership: StateAndRef<Membership.State>): SignedTransaction {
        val future = rpc.startFlowDynamic(ActivateMembershipFlow::class.java, membership)
        return future.returnValue.getOrThrow()
    }

    private fun NodeHandle.runRequestLedgerSyncFlow(members: List<Party>): Map<Party, LedgerSyncFindings> {
        val future = rpc.startFlowDynamic(RequestLedgersSyncFlow::class.java, members)
        return future.returnValue.getOrThrow()
    }

    private fun NodeHandle.runTransactionRecoveryFlow(report: Map<Party, LedgerSyncFindings>) {
        val future = rpc.startFlowDynamic(TransactionRecoveryFlow::class.java, report)
        return future.returnValue.getOrThrow()
    }

    private fun NodeHandle.runEvaluateLedgerConsistencyFlow(members: List<Party>): Map<Party, Boolean> {
        val future = rpc.startFlowDynamic(EvaluateLedgerConsistencyFlow::class.java, members)
        return future.returnValue.getOrThrow()
    }

    private fun NodeHandle.members(): List<Party> {
        val future = rpc.startFlowDynamic(GetMembershipsFlow::class.java, true)
        val result = future.returnValue.getOrThrow()
        assertTrue(result.size == participantsNodes.size)
        return result.keys.toList()
    }

    private fun NodeHandle.identity(): Party = rpc.nodeInfo().legalIdentities.first()

    /*
     * The number of states in this node's vault
     */
    private fun NodeHandle.bogusStateCount() = bogusStates().totalStatesAvailable.toInt()

    private fun NodeHandle.bogusStates(): Page<BogusState> = rpc.vaultQueryByWithPagingSpec(
            BogusState::class.java,
            VaultQueryCriteria(ALL),
            PageSpecification(1, MAX_PAGE_SIZE)
    )

    private fun NodeHandle.simulateCatastrophicFailure() {
        val future = rpc.startFlowDynamic(DestructiveFlow::class.java)
        return future.returnValue.getOrThrow()
    }

    private fun NodeHandle.createTransactions(count: Int = 1) {
        (members() - identity()).forEach { party ->
            repeat(count) {
                createTransaction(party)
            }
        }
    }

    private fun NodeHandle.createTransaction(counterParty: Party) {
        val future = rpc.startFlowDynamic(BogusFlow::class.java, counterParty)
        future.returnValue.getOrThrow()
    }
}
