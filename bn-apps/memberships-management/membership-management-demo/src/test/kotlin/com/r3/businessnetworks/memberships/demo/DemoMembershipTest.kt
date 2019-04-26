package com.r3.businessnetworks.memberships.demo

import com.r3.bno.testing.SimpleMembershipMetadata
import com.r3.businessnetworks.membership.flows.bno.ActivateMembershipFlow
import com.r3.businessnetworks.membership.flows.bno.service.DatabaseService
import com.r3.businessnetworks.membership.flows.member.RequestMembershipFlow
import com.r3.businessnetworks.memberships.demo.contracts.AssetStateCounterPartyChecks
import com.r3.businessnetworks.memberships.demo.contracts.AssetStateWithReferenceStates
import com.r3.businessnetworks.memberships.demo.flows.*
import com.r3.businessnetworks.testutilities.AbstractBusinessNetworksFlowTest
import com.r3.businessnetworks.testutilities.identity
import net.corda.core.internal.location
import net.corda.core.internal.packageName
import net.corda.core.node.services.queryBy
import net.corda.finance.POUNDS
import net.corda.finance.schemas.CashSchemaV1
import net.corda.node.services.persistence.NodeAttachmentService
import net.corda.testing.node.StartedMockNode
import org.junit.Before
import org.junit.Test

class DemoMembershipTest : AbstractBusinessNetworksFlowTest(1, 2,
        listOf("com.r3.businessnetworks.memberships.demo.contracts",
                "com.r3.businessnetworks.memberships.demo.flows",
                "com.r3.businessnetworks.membership.flows",
                "com.r3.businessnetworks.membership.states",
                "net.corda.finance.contracts.asset",
                CashSchemaV1::class.packageName)) {

    private fun bnoNode() = bnoNodes.single()
    private fun participant1Node() = participantsNodes[0]
    private fun participant2Node() = participantsNodes[1]

    @Before
    fun attachments_setup() {
        bnoNodes.forEach {
            (it.services.attachments as NodeAttachmentService).privilegedImportAttachment(
                    SimpleMembershipMetadata::class.java.location.openStream(),
                    net.corda.core.internal.DEPLOYED_CORDAPP_UPLOADER,
                    null
            )
        }
        mockNetwork.runNetwork()
    }

    @Test
    fun `test extend class`() {
        // BNO: Business Network Operator
        val bnoNode = bnoNode()
        // participants who would like to join the Business Network
        val participant1Node = participant1Node()
        val participant2Node = participant2Node()

        membershipRequestActivation(participant1Node, participant2Node, bnoNode)

        // issue asset
        participant1Node.startFlow(IssueAssetExtendClassFlow(bnoNode.identity()))
        mockNetwork.runNetwork()

        // transfer asset and cash
        val simpleState = participant1Node.services.vaultService.queryBy<AssetStateCounterPartyChecks>().states.single()
        participant1Node.startFlow(TransferAssetExtendClassFlow(bnoNode.identity(), participant2Node.identity(), simpleState, 10.POUNDS))
        mockNetwork.runNetwork()

        println("DONE")
    }

    @Test
    fun `test counterparty checks`() {
        // BNO: Business Network Operator
        val bnoNode = bnoNode()
        // participants who would like to join the Business Network
        val participant1Node = participant1Node()
        val participant2Node = participant2Node()

        membershipRequestActivation(participant1Node, participant2Node, bnoNode)

        // issue asset
        participant1Node.startFlow(IssueAssetCounterPartyChecksFlow())
        mockNetwork.runNetwork()

        // transfer asset and cash
        val simpleState = participant1Node.services.vaultService.queryBy<AssetStateCounterPartyChecks>().states.single()
        participant1Node.startFlow(TransferAssetCounterPartyChecksFlow(participant2Node.identity(), simpleState, 10.POUNDS))
        mockNetwork.runNetwork()

        println("DONE")
    }

    @Test
    fun `test membership with reference states`() {
        // BNO: Business Network Operator
        val bnoNode = bnoNode()
        // participants who would like to join the Business Network
        val participant1Node = participant1Node()
        val participant2Node = participant2Node()

        membershipRequestActivation(participant1Node, participant2Node, bnoNode)

        // issue asset
        participant1Node.startFlow(IssueAssetWithReferenceStatesFlow())
        mockNetwork.runNetwork()

        // transfer asset and cash
        val simpleState = participant1Node.services.vaultService.queryBy<AssetStateWithReferenceStates>().states.single()
        participant1Node.startFlow(TransferAssetWithReferenceStatesFlow(participant2Node.identity(), simpleState, 10.POUNDS))
        mockNetwork.runNetwork()

        println("DONE")
    }

    private fun membershipRequestActivation(participant1Node: StartedMockNode, participant2Node: StartedMockNode, bnoNode: StartedMockNode) {
        // 1a. participant1 submits a membership request to the BNO
        participant1Node.startFlow(RequestMembershipFlow(bnoNode.identity(), SimpleMembershipMetadata(role = "Group1")))
        mockNetwork.runNetwork()

        // 1b. Obtain the membership (state) for participant1 from bno
        val dbBnoNode = bnoNode.services.cordaService(DatabaseService::class.java)
        val membershipParticipant1 =
                dbBnoNode.getMembership(participant1Node.identity(), bnoNode.identity())!!

        // 1c. Activate the membership for participant1
        bnoNode.startFlow(ActivateMembershipFlow(membershipParticipant1))
        mockNetwork.runNetwork()

        // 2a. participant2 submits a membership request to the BNO
        participant2Node.startFlow(RequestMembershipFlow(bnoNode.identity(), SimpleMembershipMetadata(role = "Group2")))
        mockNetwork.runNetwork()

        // 2c. Activate the membership for participant2
        val membershipParticipant2 =
                dbBnoNode.getMembership(participant2Node.identity(), bnoNode.identity())!!
        mockNetwork.runNetwork()

        bnoNode.startFlow(ActivateMembershipFlow(membershipParticipant2))
        mockNetwork.runNetwork()
    }

}