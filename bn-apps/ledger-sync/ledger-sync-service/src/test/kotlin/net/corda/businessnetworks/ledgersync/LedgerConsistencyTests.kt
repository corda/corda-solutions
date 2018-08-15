package net.corda.businessnetworks.ledgersync

import net.corda.businessnetworks.membership.bno.ActivateMembershipFlow
import net.corda.businessnetworks.membership.bno.service.DatabaseService
import net.corda.businessnetworks.membership.member.GetMembershipsFlow
import net.corda.businessnetworks.membership.member.RequestMembershipFlow
import net.corda.businessnetworks.membership.states.Membership
import net.corda.businessnetworks.membership.states.MembershipMetadata
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault.Page
import net.corda.core.node.services.Vault.StateStatus.ALL
import net.corda.core.node.services.vault.MAX_PAGE_SIZE
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria.VaultQueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.api.ServiceHubInternal
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

@Suppress("PrivatePropertyName")
class LedgerConsistencyTests {
    private val BNO = CordaX500Name("BNO", "New York", "US")
    private val NOTARY = CordaX500Name("Notary", "London", "GB")

    private lateinit var mockNetwork: MockNetwork
    private lateinit var bnoNode: StartedMockNode
    private lateinit var participantsNodes: List<StartedMockNode>

    private val MEMBERSHIP_DATA = MembershipMetadata("DEFAULT")

    @Before
    fun start() {
        mockNetwork = MockNetwork(
                cordappPackages = listOf(
                        "net.corda.businessnetworks.membership",
                        "net.corda.businessnetworks.membership.states",
                        "net.corda.businessnetworks.ledgersync"
                ),
                notarySpecs = listOf(MockNetworkNotarySpec(NOTARY))
        )
        bnoNode = mockNetwork.createNode(BNO)
        participantsNodes = (1..3).map {
            mockNetwork.createNode(CordaX500Name("Member $it", "Paris", "FR"))
        }
        mockNetwork.runNetwork()
        participantsNodes.forEach { it.elevateToMember() }
    }

    @After
    fun stop() {
        mockNetwork.stopNodes()
    }

    @Test
    fun `members receive no ids to sync if they hold all transactions the counterparty is aware of`() {
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

    @Test
    fun `reports member ids to sync missing from requester`() {
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

    @Test
    fun `reports member ids to sync from requestee`() {
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

    @Test
    fun `ledger consistency is reported for consistent ledgers`() {
        val requester = participantsNodes[0]
        requester.createTransactions()
        val actual = requester.runEvaluateLedgerConsistencyFlow(requester.members())
        assertEquals(mapOf(
                participantsNodes[1].identity() to true,
                participantsNodes[2].identity() to true
        ), actual)
    }

    @Test
    fun `ledger consistency is reported for inconsistent ledger`() {
        val requester = participantsNodes[0]
        requester.createTransactions()
        requester.simulateCatastrophicFailure()
        val actual = requester.runEvaluateLedgerConsistencyFlow(requester.members())
        assertEquals(mapOf(
                participantsNodes[1].identity() to false,
                participantsNodes[2].identity() to false
        ), actual)
    }

    @Test
    fun `ledger consistency is reported for inconsistent counterparties`() {
        val requester = participantsNodes[0]
        requester.createTransactions()
        participantsNodes[2].simulateCatastrophicFailure()
        val actual = requester.runEvaluateLedgerConsistencyFlow(requester.members())
        assertEquals(mapOf(
                participantsNodes[1].identity() to true,
                participantsNodes[2].identity() to false
        ), actual)
    }

    @Test
    fun `transactions can be recovered`() {
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

    private fun StartedMockNode.elevateToMember() {
        runRequestMembershipFlow()
        val membership = transaction {
            val dbService = services.cordaService(DatabaseService::class.java)
            dbService.getMembership(info.legalIdentities.single()) ?: fail("Can't retrieve membership for ${this}")
        }
        bnoNode.runActivateMembershipFlow(membership)
    }

    private fun StartedMockNode.runRequestMembershipFlow(): SignedTransaction {
        val future = startFlow(RequestMembershipFlow(MEMBERSHIP_DATA))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    private fun StartedMockNode.runActivateMembershipFlow(membership: StateAndRef<Membership.State>): SignedTransaction {
        val future = startFlow(ActivateMembershipFlow(membership))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    private fun StartedMockNode.runRequestLedgerSyncFlow(members: List<Party>): Map<Party, LedgerSyncFindings> {
        val future = startFlow(RequestLedgersSyncFlow(members))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    private fun StartedMockNode.runTransactionRecoveryFlow(report: Map<Party, LedgerSyncFindings>) {
        val future = startFlow(TransactionRecoveryFlow(report))
        mockNetwork.runNetwork()
        future.getOrThrow()
    }

    private fun StartedMockNode.runEvaluateLedgerConsistencyFlow(members: List<Party>): Map<Party, Boolean> {
        val future = startFlow(EvaluateLedgerConsistencyFlow(members))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    private fun StartedMockNode.members(): List<Party> {
        val future = startFlow(GetMembershipsFlow(true))
        mockNetwork.runNetwork()
        val result = future.getOrThrow()
        assertTrue(result.size == participantsNodes.size)
        return result.keys.toList()
    }

    private fun StartedMockNode.identity() = info.legalIdentities.first()

    /*
     * The number of states in this node's vault
     */
    private fun StartedMockNode.bogusStateCount() = bogusStates().totalStatesAvailable.toInt()

    private fun StartedMockNode.bogusStates(): Page<BogusState> = transaction {
        services.vaultService.queryBy(
                BogusState::class.java,
                VaultQueryCriteria(ALL),
                PageSpecification(1, MAX_PAGE_SIZE)
        )
    }

    private fun StartedMockNode.connection() = (services as? ServiceHubInternal)?.database?.dataSource?.connection
            ?: fail("Can't obtain vault database connection")

    private fun StartedMockNode.simulateCatastrophicFailure() {
        transaction {
            connection().use { connection ->
                connection.prepareStatement("""SELECT transaction_id FROM VAULT_STATES WHERE CONTRACT_STATE_CLASS_NAME='${BogusState::class.java.canonicalName}'""").executeQuery().let { results ->
                    while (results.next()) {
                        results.getString(1).let { transactionId ->
                            connection.prepareStatement("""DELETE FROM VAULT_LINEAR_STATES_PARTS WHERE transaction_id='$transactionId'""").execute()
                            connection.prepareStatement("""DELETE FROM VAULT_LINEAR_STATES WHERE transaction_id='$transactionId'""").execute()
                            connection.prepareStatement("""DELETE FROM VAULT_STATES WHERE transaction_id='$transactionId'""").execute()
                        }
                    }
                }
            }
        }
    }

    private fun StartedMockNode.createTransactions(count: Int = 1) {
        (members() - identity()).forEach { party ->
            repeat(count) {
                createTransaction(party)
            }
        }
    }

    private fun StartedMockNode.createTransaction(counterParty: Party) {
        val future = startFlow(BogusFlow(counterParty))
        mockNetwork.runNetwork()
        future.getOrThrow()
    }
}
