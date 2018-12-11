package net.corda.businessnetworks.ledgersync

import net.corda.businessnetworks.membership.bno.ActivateMembershipFlow
import net.corda.businessnetworks.membership.member.GetMembershipsFlow
import net.corda.businessnetworks.membership.member.RequestMembershipFlow
import net.corda.businessnetworks.membership.states.MembershipState
import net.corda.businessnetworks.membership.states.SimpleMembershipMetadata
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
import net.corda.nodeapi.internal.persistence.contextDatabase
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class LedgerConsistencyTests {
    private val notary = CordaX500Name("Notary", "London", "GB")

    private val bno = CordaX500Name("BNO", "New York", "US")

    private val node1 = CordaX500Name("Member 1", "Paris", "FR")
    private val node2 = CordaX500Name("Member 2", "Paris", "FR")
    private val node3 = CordaX500Name("Member 3", "Paris", "FR")


    lateinit var nodes : MutableList<StartedMockNode>

    private lateinit var mockNetwork: MockNetwork

    @Before
    fun start() {
        mockNetwork = MockNetwork(
                cordappPackages = listOf(
                        "net.corda.businessnetworks.membership",
                        "net.corda.businessnetworks.membership.states",
                        "net.corda.businessnetworks.ledgersync"
                ),
                notarySpecs = listOf(MockNetworkNotarySpec(notary))
        )
        val bnoNode = mockNetwork.createNode(MockNodeParameters(legalName = bno))

        val node1Node = mockNetwork.createNode(MockNodeParameters(legalName = node1))
        val node2Node = mockNetwork.createNode(MockNodeParameters(legalName = node2))
        val node3Node = mockNetwork.createNode(MockNodeParameters(legalName = node3))

        nodes = mutableListOf(bnoNode, node1Node, node2Node, node3Node)

        node1Node.elevateToMember()
        node2Node.elevateToMember()
        node3Node.elevateToMember()

        mockNetwork.runNetwork()
    }

    @After
    fun stop() {
        mockNetwork.stopNodes()
    }

    @Test
    fun `members receive no ids to sync if they hold all transactions the counterparty is aware of`() {
        assertEquals(0, node1.fromNetwork().bogusStateCount())

        node1.fromNetwork().createTransactions()

        assertEquals(2, node1.fromNetwork().bogusStateCount())

        val missingTransactions = node1.fromNetwork().runRequestLedgerSyncFlow(node1.fromNetwork().members())

        assertEquals(mapOf(
                node2.fromNetwork().identity() to LedgerSyncFindings(emptyList(), emptyList()),
                node3.fromNetwork().identity() to LedgerSyncFindings(emptyList(), emptyList())
        ), missingTransactions)
    }

    @Test
    fun `reports member ids to sync missing from requester`() {
        node1.fromNetwork().createTransactions()
        assertEquals(2, node1.fromNetwork().bogusStateCount())
        node1.fromNetwork().simulateCatastrophicFailure()
        assertEquals(0, node1.fromNetwork().bogusStateCount())
        node1.fromNetwork().runRequestLedgerSyncFlow(node1.fromNetwork().members())

        val missingTransactions = node1.fromNetwork().runRequestLedgerSyncFlow(node1.fromNetwork().members())

        assertEquals(1, missingTransactions[node2.fromNetwork().identity()]!!.missingAtRequester.size)
        assertEquals(1, missingTransactions[node3.fromNetwork().identity()]!!.missingAtRequester.size)
    }

    @Test
    fun `reports member ids to sync from requestee`() {
        node1.fromNetwork().createTransactions()
        node2.fromNetwork().simulateCatastrophicFailure()
        assertEquals(0, node2.fromNetwork().bogusStateCount())

        node3.fromNetwork().simulateCatastrophicFailure()
        assertEquals(0, node3.fromNetwork().bogusStateCount())

        node1.fromNetwork().runRequestLedgerSyncFlow(node1.fromNetwork().members())

        val missingTransactions = node1.fromNetwork().runRequestLedgerSyncFlow(node1.fromNetwork().members())

        assertEquals(1, missingTransactions[node2.fromNetwork().identity()]!!.missingAtRequestee.size)
        assertEquals(0, missingTransactions[node2.fromNetwork().identity()]!!.missingAtRequester.size)
        assertEquals(1, missingTransactions[node3.fromNetwork().identity()]!!.missingAtRequestee.size)
        assertEquals(0, missingTransactions[node3.fromNetwork().identity()]!!.missingAtRequester.size)
    }

    @Test
    fun `ledger consistency is reported for consistent ledgers`() {
        node1.fromNetwork().createTransactions()
        val actual = node1.fromNetwork().runEvaluateLedgerConsistencyFlow(node1.fromNetwork().members())
        assertEquals(mapOf(
                node2.fromNetwork().identity() to true,
                node3.fromNetwork().identity() to true
        ), actual)
    }

    @Test
    fun `ledger consistency is reported for inconsistent ledger`() {
        node1.fromNetwork().createTransactions()
        node1.fromNetwork().simulateCatastrophicFailure()
        val actual = node1.fromNetwork().runEvaluateLedgerConsistencyFlow(node1.fromNetwork().members())
        assertEquals(mapOf(
                node2.fromNetwork().identity() to false,
                node3.fromNetwork().identity() to false
        ), actual)
    }

    @Test
    fun `ledger consistency is reported for inconsistent counterparties`() {
        node1.fromNetwork().createTransactions()
        node3.fromNetwork().simulateCatastrophicFailure()
        val actual = node1.fromNetwork().runEvaluateLedgerConsistencyFlow(node1.fromNetwork().members())
        assertEquals(mapOf(
                node2.fromNetwork().identity() to true,
                node3.fromNetwork().identity() to false
        ), actual)
    }

    @Test
    fun `transactions can be recovered`() {
        node1.fromNetwork().createTransactions(3)

        assertEquals(6, node1.fromNetwork().bogusStateCount())

        node1.fromNetwork().simulateCatastrophicFailure()

        val consistencyResult = node1.fromNetwork().runEvaluateLedgerConsistencyFlow(node1.fromNetwork().members())

        assertEquals(mapOf(
                node2.fromNetwork().identity() to false,
                node3.fromNetwork().identity() to false
        ), consistencyResult)

        assertEquals(0, node1.fromNetwork().bogusStateCount())

        val ledgerSyncResult = node1.fromNetwork().runRequestLedgerSyncFlow(node1.fromNetwork().members())

        assertEquals(3, ledgerSyncResult[node2.fromNetwork().identity()]!!.missingAtRequester.size)
        assertEquals(3, ledgerSyncResult[node3.fromNetwork().identity()]!!.missingAtRequester.size)

        node1.fromNetwork().runTransactionRecoveryFlow(ledgerSyncResult)

        assertEquals(6, node1.fromNetwork().bogusStateCount())
    }

    private fun StartedMockNode.elevateToMember() : StartedMockNode{
        val membershipRequest = runRequestMembershipFlow().coreTransaction.outRef<MembershipState<Any>>(0)
        bno.fromNetwork().runActivateMembershipFlow(membershipRequest)
        return this
    }

    private fun StartedMockNode.runRequestMembershipFlow(): SignedTransaction {
        val bnoParty = services.identityService.wellKnownPartyFromX500Name(bno)!!
        val future = startFlow(RequestMembershipFlow(bnoParty, SimpleMembershipMetadata("DEFAULT")))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    private fun StartedMockNode.runActivateMembershipFlow(membership: StateAndRef<MembershipState<Any>>): SignedTransaction {
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
        val bnoParty = services.identityService.wellKnownPartyFromX500Name(bno)!!
        val future = startFlow(GetMembershipsFlow(bnoParty, forceRefresh = true))
        mockNetwork.runNetwork()
        return future.getOrThrow().keys.toList()
    }

    private fun StartedMockNode.identity() = info.legalIdentities.first()

    /*
     * The number of states in this node's vault
     */
    private fun StartedMockNode.bogusStateCount() = bogusStates().totalStatesAvailable.toInt()

    private fun StartedMockNode.bogusStates(): Page<BogusState> = contextDatabase.transaction {
        services.vaultService.queryBy(
                BogusState::class.java,
                VaultQueryCriteria(ALL),
                PageSpecification(1, MAX_PAGE_SIZE)
        )
    }

    private fun StartedMockNode.simulateCatastrophicFailure() {
        contextDatabase.transaction {
            connection.prepareStatement("""SELECT transaction_id FROM VAULT_STATES WHERE CONTRACT_STATE_CLASS_NAME='${BogusState::class.java.canonicalName}'""").executeQuery().let { results ->
                while (results.next()) {
                    results.getString(1).let { transactionId ->
                        connection.prepareStatement("""DELETE FROM NODE_TRANSACTIONS WHERE tx_id='$transactionId'""").execute()
                        connection.prepareStatement("""DELETE FROM VAULT_LINEAR_STATES_PARTS WHERE transaction_id='$transactionId'""").execute()
                        connection.prepareStatement("""DELETE FROM VAULT_LINEAR_STATES WHERE transaction_id='$transactionId'""").execute()
                        connection.prepareStatement("""DELETE FROM VAULT_STATES WHERE transaction_id='$transactionId'""").execute()
                    }
                }
            }
        }

        restart()
    }


    private fun CordaX500Name.fromNetwork(): StartedMockNode = nodes.lastOrNull {
        it.services.myInfo.legalIdentities.single().name == this
    }!!

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

    private fun StartedMockNode.restart() {
        stop()
        nodes.remove(this)
        mockNetwork.createNode(
                MockNodeParameters(legalName = services.myInfo.legalIdentities.single().name, forcedID = id)
        )

    }
}
