package net.corda.businessnetworks.membership

import net.corda.businessnetworks.membership.bno.OnMembershipChanged
import net.corda.businessnetworks.membership.states.MembershipContract
import net.corda.businessnetworks.membership.states.MembershipState
import net.corda.businessnetworks.membership.states.SimpleMembershipMetadata
import net.corda.core.flows.FlowException
import org.junit.Test
import kotlin.test.assertEquals

class AmendMembershipMetadataFlowTest : AbstractFlowTest(2) {
    override fun registerFlows() {
        participantsNodes.forEach {
            it.registerInitiatedFlow(NotificationsCounterFlow::class.java)
        }
    }

    @Test
    fun `amend metadata happy path`() {
        val memberNode = participantsNodes.first()
        val memberParty = identity(memberNode)

        participantsNodes.forEach {
            runRequestMembershipFlow(it)
            runActivateMembershipFlow(bnoNode, identity(it))
        }

        // cleaning up the received notifications as we are interested in the notifications related to metadata amendment only
        NotificationsCounterFlow.NOTIFICATIONS.clear()

        val existingMembership = getMembership(memberNode, memberParty)
        val newMetadata = existingMembership.state.data.membershipMetadata.copy(role = "Some other role")

        val partiallySignedTx = runAmendMetadataFlow(memberNode, newMetadata)
        val allMemberTxs = allTransactions(memberNode)
        val allSignedTx = allMemberTxs.single { it.id ==  partiallySignedTx.id}
        allSignedTx.verifyRequiredSignatures()

        val outputWithContract = allSignedTx.tx.outputs.single()
        val outputMembership = outputWithContract.data as MembershipState<*>
        val command = allSignedTx.tx.commands.single()

        assert(command.value is MembershipContract.Commands.Amend)
        assert(outputWithContract.contract == MembershipContract.CONTRACT_NAME)
        assert(outputMembership.membershipMetadata == newMetadata)
        assert(allSignedTx.inputs.single() == existingMembership.ref)

        // all members should have received the same notification
        val amendedMembership = getMembership(bnoNode, memberParty)
        val expectedNotifications = participantsNodes.map { NotificationHolder(identity(it), bnoParty, OnMembershipChanged(amendedMembership)) }.toSet()
        assertEquals(expectedNotifications, NotificationsCounterFlow.NOTIFICATIONS.toSet())
    }

    @Test
    fun `non members should be unable to amend their metadata`() {
        val memberNode = participantsNodes.first()
        val memberParty = identity(memberNode)

        runRequestMembershipFlow(memberNode)
        runActivateMembershipFlow(bnoNode, memberParty)

        try {
            runAmendMetadataFlow(memberNode, SimpleMembershipMetadata(role="Some role"))
        } catch (e : FlowException) {
            assert("$memberParty is not a member" == e.message)
        }
    }
}