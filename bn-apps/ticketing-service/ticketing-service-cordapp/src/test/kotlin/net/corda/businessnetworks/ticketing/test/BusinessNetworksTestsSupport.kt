package net.corda.businessnetworks.ticketing.test

import net.corda.businessnetworks.membership.bno.*
import net.corda.businessnetworks.membership.member.RequestMembershipFlow
import net.corda.businessnetworks.membership.states.SimpleMembershipMetadata
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode

abstract class BusinessNetworksTestsSupport(val additionalPackages : List<String>, val threadPerNode : Boolean = false) {

    val notaryName = CordaX500Name.parse("O=Notary,L=London,C=GB")
    val bnoName = CordaX500Name.parse("O=BNO,L=New York,C=US")

    lateinit var mockNetwork: MockNetwork
    lateinit var bnoNode: StartedMockNode
    lateinit var participantNodes: List<StartedMockNode>

    fun createNetworkAndRunTest(numberOfParticipants : Int, grantMemberships : Boolean, test : ()->Unit) {
        createNetwork(numberOfParticipants)
        try {
            if(grantMemberships) {
                grantMemberships()
            }
            test()
        } finally {
            stopNetwork()
        }
    }

    open protected fun runNetwork() {
        if(threadPerNode) {
            Thread.sleep(3000) //3s is rather arbitrary. It's to ensure that things take place before proceeding to assertions.
        } else {
            mockNetwork.runNetwork()
        }
    }

    protected open fun createNetwork(participants : Int) {
        mockNetwork = MockNetwork(cordappPackages = listOf(
                                    "net.corda.businessnetworks.membership",
                                    "net.corda.businessnetworks.membership.states") + additionalPackages,
                notarySpecs = listOf(MockNetworkNotarySpec(notaryName)), threadPerNode = threadPerNode)
        bnoNode = mockNetwork.createNode(MockNodeParameters(legalName = bnoName))

        participantNodes = (1..participants).map {
            mockNetwork.createNode(MockNodeParameters(legalName = CordaX500Name.parse("O=Participant $it,L=London,C=GB")))
        }

        runNetwork()
    }

    protected open fun stopNetwork() {
        mockNetwork.stopNodes()
    }

    protected open fun grantMemberships() {
        participantNodes.forEach {
            runRequestMembershipFlow(it)
            runActivateMembershipForPartyFlow(bnoNode, it.info.legalIdentities.first())
        }
    }

    fun runRequestMembershipFlow(nodeToRunTheFlow : StartedMockNode, membershipMetadata : SimpleMembershipMetadata = SimpleMembershipMetadata(role="DEFAULT")) : SignedTransaction {
        val future = nodeToRunTheFlow.startFlow(RequestMembershipFlow(membershipMetadata))
        runNetwork()
        return future.getOrThrow()
    }

    fun runActivateMembershipForPartyFlow(nodeToRunTheFlow : StartedMockNode, party : Party) : SignedTransaction {
        val future = nodeToRunTheFlow.startFlow(ActivateMembershipForPartyFlow(party))
        runNetwork()
        return future.getOrThrow()
    }


}

fun StartedMockNode.party() : Party {
    return this.info.legalIdentities.single()
}