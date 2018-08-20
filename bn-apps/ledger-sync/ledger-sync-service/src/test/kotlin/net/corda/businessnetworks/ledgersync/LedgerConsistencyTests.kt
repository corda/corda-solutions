package net.corda.businessnetworks.ledgersync

import net.corda.businessnetworks.membership.bno.ActivateMembershipFlow
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
import net.corda.node.internal.StartedNode
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNetwork.MockNode
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.startFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Suppress("PrivatePropertyName")
class LedgerConsistencyTests {
    private val BNO = CordaX500Name("BNO", "New York", "US")
    private val NOTARY = CordaX500Name("Notary", "London", "GB")

    private lateinit var mockNetwork: InternalMockNetwork
    private lateinit var bnoNode: StartedNode<MockNode>
    private lateinit var participantsNodes: List<StartedNode<MockNode>>

    private val MEMBERSHIP_DATA = MembershipMetadata("DEFAULT")

    @Before
    fun start() {
        mockNetwork = InternalMockNetwork(
                cordappPackages = listOf(
                        "net.corda.businessnetworks.membership",
                        "net.corda.businessnetworks.membership.states",
                        "net.corda.businessnetworks.ledgersync"
                ),
                notarySpecs = listOf(MockNetworkNotarySpec(NOTARY))
        )
        bnoNode = mockNetwork.createNode(InternalMockNodeParameters(legalName = BNO))

        participantsNodes = (1..3).map {
            mockNetwork.createNode(InternalMockNodeParameters(legalName = CordaX500Name("Member $it", "Paris", "FR")))
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

    private fun StartedNode<MockNode>.elevateToMember() {
        val membershipRequest = runRequestMembershipFlow().coreTransaction.outRef<Membership.State>(0)
        bnoNode.runActivateMembershipFlow(membershipRequest)
    }

    private fun StartedNode<MockNode>.runRequestMembershipFlow(): SignedTransaction {
        val future = services.startFlow(RequestMembershipFlow(MEMBERSHIP_DATA)).resultFuture
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    private fun StartedNode<MockNode>.runActivateMembershipFlow(membership: StateAndRef<Membership.State>): SignedTransaction {
        val future = services.startFlow(ActivateMembershipFlow(membership)).resultFuture
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    private fun StartedNode<MockNode>.runRequestLedgerSyncFlow(members: List<Party>): Map<Party, LedgerSyncFindings> {
        val future = services.startFlow(RequestLedgersSyncFlow(members)).resultFuture
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    private fun StartedNode<MockNode>.runTransactionRecoveryFlow(report: Map<Party, LedgerSyncFindings>) {
        val future = services.startFlow(TransactionRecoveryFlow(report)).resultFuture
        mockNetwork.runNetwork()
        future.getOrThrow()
    }

    private fun StartedNode<MockNode>.runEvaluateLedgerConsistencyFlow(members: List<Party>): Map<Party, Boolean> {
        val future = services.startFlow(EvaluateLedgerConsistencyFlow(members)).resultFuture
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    private fun StartedNode<MockNode>.members(): List<Party> {
        val future = services.startFlow(GetMembershipsFlow(true)).resultFuture
        mockNetwork.runNetwork()
        val result = future.getOrThrow()
        assertTrue(result.size == participantsNodes.size)
        return result.keys.toList()
    }

    private fun StartedNode<MockNode>.identity() = info.legalIdentities.first()

    /*
     * The number of states in this node's vault
     */
    private fun StartedNode<MockNode>.bogusStateCount() = bogusStates().totalStatesAvailable.toInt()

    private fun StartedNode<MockNode>.bogusStates(): Page<BogusState> = database.transaction {
        services.vaultService.queryBy(
                BogusState::class.java,
                VaultQueryCriteria(ALL),
                PageSpecification(1, MAX_PAGE_SIZE)
        )
    }

    private fun StartedNode<MockNode>.simulateCatastrophicFailure() {
        services.database.transaction {
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
        // FIXME restart
    }

    private fun StartedNode<MockNode>.createTransactions(count: Int = 1) {
        (members() - identity()).forEach { party ->
            repeat(count) {
                createTransaction(party)
            }
        }
    }

    private fun StartedNode<MockNode>.createTransaction(counterParty: Party) {
        val future = services.startFlow(BogusFlow(counterParty)).resultFuture
        mockNetwork.runNetwork()
        future.getOrThrow()
    }
}
