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
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

// TODO moritzplatt 07/08/2018 --
// add readme + demo flow
// run against enterprise
//  - download dev pack with maven structure
//  - re-test by changing target corda version in gradle

@Suppress("PrivatePropertyName")
class RequestLedgersSyncFlowTest {
    private val NOTARY = CordaX500Name("Notary", "London", "GB")
    private val BNO = CordaX500Name("BNO", "New York", "US")
    private val NON_MEMBER = CordaX500Name("Non-Member", "Dublin", "IE")

    private lateinit var mockNetwork: MockNetwork
    private lateinit var bnoNode: StartedMockNode
    private lateinit var participantsNodes: List<StartedMockNode>
    private lateinit var nonMemberNode: StartedMockNode

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
        nonMemberNode = mockNetwork.createNode(NON_MEMBER)
        mockNetwork.runNetwork()
        participantsNodes.forEach { it.elevateToMember() }
    }

    @After
    fun stop() {
        mockNetwork.stopNodes()
    }

    @Test
    fun `members receive no ids to sync if they hold all transactions the counter party is aware of`() {
        val requester = participantsNodes[0]
        val missingTransactions = requester.runRequestLedgerSyncFlow(requester.members())

        assertEquals(mapOf(
                participantsNodes[1].identity() to LedgerSyncFindings(emptyList(), emptyList()),
                participantsNodes[2].identity() to LedgerSyncFindings(emptyList(), emptyList())
        ), missingTransactions)
    }

    @Test
    fun `members ids sync if they only hold a subset of transactions the counter party is aware of`() {
        TODO()

        // how to test: remove an event through a raw jdbc connection on serviceshub
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

    private fun StartedMockNode.members(): List<Party> {
        val future = startFlow(GetMembershipsFlow(true))
        mockNetwork.runNetwork()
        val result = future.getOrThrow()
        assertTrue(result.size == participantsNodes.size)
        return result.keys.toList()
    }

    private fun StartedMockNode.identity() = info.legalIdentities.first()


}
