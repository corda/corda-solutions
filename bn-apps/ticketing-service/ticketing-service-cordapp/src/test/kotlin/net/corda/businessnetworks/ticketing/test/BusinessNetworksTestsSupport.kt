package net.corda.businessnetworks.ticketing.test

import net.corda.businessnetworks.membership.bno.*
import net.corda.businessnetworks.membership.member.RequestMembershipFlow
import net.corda.businessnetworks.membership.bno.service.DatabaseService
import net.corda.businessnetworks.membership.states.MembershipState
import net.corda.businessnetworks.membership.states.SimpleMembershipMetadata
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode

abstract class BusinessNetworksTestsSupport {

    val notaryName = CordaX500Name.parse("O=Notary,L=London,C=GB")
    val bnoName = CordaX500Name.parse("O=BNO,L=New York,C=US")

    lateinit var mockNetwork: MockNetwork
    lateinit var bnoNode: StartedMockNode
    lateinit var participantNodes: List<StartedMockNode>

    fun createNetworkAndRunTest(numberOfParticipants : Int, test : ()->Unit) {
        createNetwork(numberOfParticipants)
        try {
            test()
        } finally {
            stopNetwork()
        }
    }

    protected open fun createNetwork(participants : Int) {
        mockNetwork = MockNetwork(cordappPackages = listOf(
                                    "net.corda.businessnetworks.membership",
                                    "net.corda.businessnetworks.membership.states"),
                notarySpecs = listOf(MockNetworkNotarySpec(notaryName)))
        bnoNode = mockNetwork.createNode(MockNodeParameters(legalName = bnoName))

        participantNodes = (1..participants).map {
            mockNetwork.createNode(MockNodeParameters(legalName = CordaX500Name.parse("O=Participant $it,L=London,C=GB")))
        }

        registerFlows()
        mockNetwork.runNetwork()
    }

    protected open fun stopNetwork() {
        mockNetwork.stopNodes()
    }

    abstract fun registerFlows()




    fun runRequestMembershipFlow(nodeToRunTheFlow : StartedMockNode, membershipMetadata : SimpleMembershipMetadata = SimpleMembershipMetadata(role="DEFAULT")) : SignedTransaction {
        val future = nodeToRunTheFlow.startFlow(RequestMembershipFlow(membershipMetadata))
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

    fun getMembership (node : StartedMockNode, party : Party) = node.transaction {
        val dbService = node.services.cordaService(DatabaseService::class.java)
        dbService.getMembership(party)!! as StateAndRef<MembershipState<SimpleMembershipMetadata>>
    }



}