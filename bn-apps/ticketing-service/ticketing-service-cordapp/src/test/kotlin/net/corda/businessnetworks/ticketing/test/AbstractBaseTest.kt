package net.corda.businessnetworks.ticketing.test

import net.corda.businessnetworks.membership.bno.*
import net.corda.businessnetworks.membership.member.AmendMembershipMetadataFlow
import net.corda.businessnetworks.membership.member.GetMembershipsFlow
import net.corda.businessnetworks.membership.member.RequestMembershipFlow
import net.corda.businessnetworks.membership.bno.service.DatabaseService
import net.corda.businessnetworks.membership.states.Membership
import net.corda.businessnetworks.membership.states.MembershipMetadata
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.internal.chooseIdentityAndCert
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before

abstract class AbstractBaseTest(val numberOfMembers : Int) {

    val notaryName = CordaX500Name.parse("O=Notary,L=London,C=GB")
    val bnoName = CordaX500Name.parse("O=BNO,L=New York,C=US")

    lateinit var mockNetwork: MockNetwork
    lateinit var bnoNode: StartedMockNode
    lateinit var memberNodes: List<StartedMockNode>

    lateinit var bnoParty : Party

    @Before
    open fun setup() {
        mockNetwork = MockNetwork(cordappPackages = listOf("net.corda.businessnetworks.membership",
                "net.corda.businessnetworks.membership.states"),
                notarySpecs = listOf(MockNetworkNotarySpec(notaryName)))
        bnoNode = createNode(bnoName, true)
        memberNodes = (1..numberOfMembers).map {
            createNode(CordaX500Name.parse("O=Participant $it,L=London,C=GB"))
        }
        registerFlows()
        mockNetwork.runNetwork()
        bnoParty = bnoNode.info.chooseIdentityAndCert().party

        memberNodes.forEach { obtainMembership(it) }
    }

    fun obtainMembership(memberNode : StartedMockNode) {
        runRequestMembershipFlow(memberNode)
        runActivateMembershipForPartyFlow(bnoNode, identity(memberNode))
    }

    fun runRequestMembershipFlow(nodeToRunTheFlow : StartedMockNode, membershipMetadata : MembershipMetadata = MembershipMetadata(role="DEFAULT")) : SignedTransaction {
        val future = nodeToRunTheFlow.startFlow(RequestMembershipFlow(membershipMetadata))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    fun runActivateMembershipForPartyFlow(nodeToRunTheFlow : StartedMockNode, party : Party) : SignedTransaction {
        val future = nodeToRunTheFlow.startFlow(ActivateMembershipForPartyFlow(party))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    fun registerFlows() {
        bnoNode.registerInitiatedFlow(RequestMembershipFlowResponder::class.java)
    }

    fun createNode(name : CordaX500Name, isBno : Boolean = false) =
            mockNetwork.createNode(MockNodeParameters(legalName = name))

    @After
    open fun tearDown() {
        mockNetwork.stopNodes()
    }

    fun identity(node : StartedMockNode) = node.info.legalIdentities.single()
}