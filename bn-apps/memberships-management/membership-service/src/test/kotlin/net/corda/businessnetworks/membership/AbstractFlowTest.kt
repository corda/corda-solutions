package net.corda.businessnetworks.membership

import net.corda.businessnetworks.membership.bno.ActivateMembershipFlow
import net.corda.businessnetworks.membership.bno.ActivateMembershipForPartyFlow
import net.corda.businessnetworks.membership.bno.SuspendMembershipFlow
import net.corda.businessnetworks.membership.bno.SuspendMembershipForPartyFlow
import net.corda.businessnetworks.membership.member.AmendMembershipMetadataFlow
import net.corda.businessnetworks.membership.member.GetMembershipsFlow
import net.corda.businessnetworks.membership.member.RequestMembershipFlow
import net.corda.businessnetworks.membership.bno.service.DatabaseService
import net.corda.businessnetworks.membership.states.MembershipState
import net.corda.businessnetworks.membership.states.SimpleMembershipMetadata
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
import java.io.File

abstract class AbstractFlowTest(
        val numberOfIdentities : Int
) {

    val notaryName = CordaX500Name.parse("O=Notary,L=London,C=GB")
    val bnoName = CordaX500Name.parse("O=BNO,L=New York,C=US")

    lateinit var mockNetwork: MockNetwork
    lateinit var bnoNode: StartedMockNode
    lateinit var participantsNodes: List<StartedMockNode>

    lateinit var bnoParty : Party

    @Before
    open fun setup() {
        mockNetwork = MockNetwork(cordappPackages = listOf("net.corda.businessnetworks.membership",
                "net.corda.businessnetworks.membership.states"),
                notarySpecs = listOf(MockNetworkNotarySpec(notaryName)))
        bnoNode = createNode(bnoName, true)
        participantsNodes = (1..numberOfIdentities).map {
            createNode(CordaX500Name.parse("O=Participant $it,L=London,C=GB"))
        }
        registerFlows()
        mockNetwork.runNetwork()
        bnoParty = bnoNode.info.chooseIdentityAndCert().party
    }

    abstract fun registerFlows()

    fun createNode(name : CordaX500Name, isBno : Boolean = false) =
            mockNetwork.createNode(MockNodeParameters(legalName = name))

    @After
    open fun tearDown() {
        mockNetwork.stopNodes()
    }

    fun runRequestMembershipFlow(nodeToRunTheFlow : StartedMockNode, membershipMetadata : SimpleMembershipMetadata = SimpleMembershipMetadata(role="DEFAULT")) : SignedTransaction {
        val future = nodeToRunTheFlow.startFlow(RequestMembershipFlow(membershipMetadata))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    fun runSuspendMembershipFlow(nodeToRunTheFlow : StartedMockNode, party : Party) : SignedTransaction {
        val membership = getMembership(nodeToRunTheFlow, party)
        val future = nodeToRunTheFlow.startFlow(SuspendMembershipFlow(membership))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    fun runSuspendMembershipForPartyFlow(nodeToRunTheFlow : StartedMockNode, party: Party) : SignedTransaction {
        val future = nodeToRunTheFlow.startFlow(SuspendMembershipForPartyFlow(party))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    fun runActivateMembershipFlow(nodeToRunTheFlow : StartedMockNode, party : Party) : SignedTransaction {
        val membership = getMembership(nodeToRunTheFlow, party)
        val future = nodeToRunTheFlow.startFlow(ActivateMembershipFlow(membership))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    fun runActivateMembershipForPartyFlow(nodeToRunTheFlow : StartedMockNode, party : Party) : SignedTransaction {
        val future = nodeToRunTheFlow.startFlow(ActivateMembershipForPartyFlow(party))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    fun runAmendMetadataFlow(nodeToRunTheFlow : StartedMockNode, newMetadata : SimpleMembershipMetadata) : SignedTransaction {
        val future = nodeToRunTheFlow.startFlow(AmendMembershipMetadataFlow(newMetadata))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    fun runGetMembershipsListFlow(nodeToRunTheFlow : StartedMockNode, force : Boolean, filterOutNotExisting : Boolean = true) : Map<Party, StateAndRef<MembershipState<Any>>> {
        val future = nodeToRunTheFlow.startFlow(GetMembershipsFlow(force, filterOutNotExisting))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    fun getMembership (node : StartedMockNode, party : Party) = node.transaction {
            val dbService = node.services.cordaService(DatabaseService::class.java)
            dbService.getMembership(party)!! as StateAndRef<MembershipState<SimpleMembershipMetadata>>
        }

    fun allTransactions(node : StartedMockNode) = node.transaction {
        node.services.validatedTransactions.track().snapshot
    }

    fun identity(node : StartedMockNode) = node.info.legalIdentities.single()

    fun fileFromClasspath(fileName : String) = File(AbstractFlowTest::class.java.classLoader.getResource(fileName).toURI())
}