package net.corda.businessnetworks.membership

import net.corda.businessnetworks.membership.bno.OnMembershipChanged
import net.corda.businessnetworks.membership.states.MembershipContract
import net.corda.businessnetworks.membership.states.MembershipState
import net.corda.businessnetworks.membership.states.SimpleMembershipMetadata
import net.corda.core.flows.FlowException
import org.junit.Test
import kotlin.test.assertEquals

class AmendMembershipMetadataFlowTest : AbstractFlowTest(
        numberOfBusinessNetworks = 1,
        numberOfParticipants = 2,
        participantRespondingFlows = listOf(NotificationsCounterFlow::class.java)) {

    @Test
    fun `amend metadata happy path`() {
        val bnoNode = bnoNodes.first()
        val participantNode = participantsNodes.first()

        runRequestAndActivateMembershipFlow(bnoNode, participantsNodes)

        // cleaning up the received notifications as we are interested in the notifications related to metadata amendment only
        NotificationsCounterFlow.NOTIFICATIONS.clear()

        val existingMembership = getMembership(participantNode, participantNode.identity())
        val newMetadata = (existingMembership.state.data.membershipMetadata as SimpleMembershipMetadata).copy(role = "Some other role")

        val partiallySignedTx = runAmendMetadataFlow(bnoNode, participantNode, newMetadata)
        val allSignedTx = allTransactions(participantNode).single { it.id ==  partiallySignedTx.id}
        allSignedTx.verifyRequiredSignatures()

        val outputWithContract = allSignedTx.tx.outputs.single()
        val outputMembership = outputWithContract.data as MembershipState<*>
        val command = allSignedTx.tx.commands.single()

        assert(command.value is MembershipContract.Commands.Amend)
        assert(outputWithContract.contract == MembershipContract.CONTRACT_NAME)
        assert(outputMembership.membershipMetadata == newMetadata)
        assert(allSignedTx.inputs.single() == existingMembership.ref)

        // all members should have received the same notification
        val amendedMembership = getMembership(bnoNode, participantNode.identity())
        val expectedNotifications = participantsNodes.map { NotificationHolder(it.identity(), bnoNode.identity(), OnMembershipChanged(amendedMembership)) }.toSet()
        assertEquals(expectedNotifications, NotificationsCounterFlow.NOTIFICATIONS)
    }


    @Test
    fun `non members should not be able to amend their metadata`() {
        val bnoNode = bnoNodes.first()
        val memberNode = participantsNodes.first()

        runRequestMembershipFlow(bnoNode, memberNode)
        runActivateMembershipFlow(bnoNode, memberNode.identity())

        try {
            runAmendMetadataFlow(bnoNode, memberNode, SimpleMembershipMetadata(role="Some role"))
        } catch (e : FlowException) {
            assert("${memberNode.identity()} is not a member" == e.message)
        }
    }
}