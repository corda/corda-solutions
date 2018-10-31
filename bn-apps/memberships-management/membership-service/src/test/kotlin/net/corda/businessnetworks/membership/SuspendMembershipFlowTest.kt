package net.corda.businessnetworks.membership

import net.corda.businessnetworks.membership.bno.OnMembershipChanged
import net.corda.businessnetworks.membership.bno.SuspendMembershipFlow
import net.corda.businessnetworks.membership.states.MembershipContract
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.StartedMockNode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class SuspendMembershipFlowTest : AbstractFlowTest(numberOfBusinessNetworks = 2,
        numberOfParticipants = 3,
        participantRespondingFlows = listOf(NotificationsCounterFlow::class.java)) {

    private fun testMembershipSuspension(suspender : (bnoNode : StartedMockNode, participantNode : StartedMockNode) -> SignedTransaction) {
        val bnoNode = bnoNodes.first()
        val suspendedMemberNode = participantsNodes.first()

        runrequestAndActivateMembershipFlow(bnoNode, participantsNodes)

        // cleaning up notifications as we are interested in SUSPENDs only
        NotificationsCounterFlow.NOTIFICATIONS.clear()

        val inputMembership = getMembership(suspendedMemberNode, suspendedMemberNode.identity())
        val stx = suspender(bnoNode, suspendedMemberNode)
        stx.verifyRequiredSignatures()

        val outputTxState = stx.tx.outputs.single()
        val command = stx.tx.commands.single()

        assert(MembershipContract.CONTRACT_NAME == outputTxState.contract)
        assert(command.value is MembershipContract.Commands.Suspend)
        assert(stx.inputs.single() == inputMembership.ref)

        // both the active and the suspended member should have received the same notification
        val suspendedMembership = getMembership(bnoNode, suspendedMemberNode.identity())
        val expectedNotifications = participantsNodes.map { NotificationHolder(it.identity(), bnoNode.identity(), OnMembershipChanged(suspendedMembership)) }.toSet()
        assertEquals(expectedNotifications, NotificationsCounterFlow.NOTIFICATIONS)
    }

    @Test
    fun `membership suspension happy path`() = testMembershipSuspension { bnoNode, participantNode -> runSuspendMembershipFlow(bnoNode, participantNode.identity()) }

    @Test
    fun `membership suspension should succeed when using a convenience flow`() = testMembershipSuspension { bnoNode, participantNode -> runSuspendMembershipForPartyFlow(bnoNode, participantNode.identity()) }

    @Test
    fun `only BNO should be able to start the flow`() {
        val bnoNode = bnoNodes.first()
        val memberNode = participantsNodes.first()

        runRequestMembershipFlow(bnoNode, memberNode)
        try {
            val membership = getMembership(memberNode, memberNode.identity())
            val future = memberNode.startFlow(SuspendMembershipFlow(membership))
            mockNetwork.runNetwork()
            future.getOrThrow()
            fail()
        } catch (e : NotBNOException) {
            assertEquals("This node is not the business network operator for this membership", e.message)
        }
    }

    @Test
    fun `nodes shouldn't be able to transact on business networks where their membership is suspended`() {
        val bno1Node = bnoNodes[0]
        val bno2Node = bnoNodes[1]

        val multiBnNode= participantsNodes[0] // participates on both of the BNs
        val bn1ParticipantNode = participantsNodes[1] // participates in BN1 only
        val bn2ParticipantNode = participantsNodes[2] // participates in BN2 only

        // registering BN-specific initiated flows
        bn1ParticipantNode.registerInitiatedFlow(BN_1_RespondingFlow::class.java)
        bn2ParticipantNode.registerInitiatedFlow(BN_2_RespondingFlow::class.java)

        // requesting and activating memberships
        runrequestAndActivateMembershipFlow(bno1Node, listOf(multiBnNode, bn1ParticipantNode))
        runrequestAndActivateMembershipFlow(bno2Node, listOf(multiBnNode, bn2ParticipantNode))

        // suspending multiBnNode's membership in BN2
        runSuspendMembershipFlow(bno2Node, multiBnNode.identity())

        val future1 = multiBnNode.startFlow(BN_1_InitiatingFlow(bn1ParticipantNode.identity()))
        val future2 = multiBnNode.startFlow(BN_2_InitiatingFlow(bn2ParticipantNode.identity()))

        mockNetwork.runNetwork()

        future1.getOrThrow()
        // future 2 should fail as multiBnNode's membership is suspended in this business network
        try {
            future2.getOrThrow()
            fail()
        } catch (ex : NotAMemberException) {
        }
    }
}