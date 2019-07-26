package com.r3.businessnetworks.membership.flows

import com.r3.businessnetworks.membership.flows.bno.OnMembershipChanged
import com.r3.businessnetworks.membership.flows.bno.SelfIssueMembershipFlow
import com.r3.businessnetworks.membership.flows.bno.SuspendMembershipFlow
import com.r3.businessnetworks.membership.states.MembershipContract
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.StartedMockNode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class SelfIssueMembershipFlowTest : AbstractFlowTest(
        numberOfBusinessNetworks = 2,
        numberOfParticipants = 4,
        participantRespondingFlows = listOf(NotificationsCounterFlow::class.java)) {

    @Test
    fun `self issue happy path`() {
        val bnoNode = bnoNodes.first()
        val participantNode = participantsNodes.first()

        runRequestMembershipFlow(bnoNode, bnoNode)
        // membership state before activation
        val inputMembership = getMembership(bnoNode, bnoNode.identity(), bnoNode.identity())
        val stx = runSelfIssueMembershipFlow(bnoNode)
        ////stx.inputs.

        val outputTxState = stx.tx.outputs.single()
        val command = stx.tx.commands.single()

        assert(MembershipContract.CONTRACT_NAME == outputTxState.contract)
        assert(command.value is MembershipContract.Commands.Activate)
        assert(stx.tx.inputs.single() == inputMembership.ref)

        stx.verifyRequiredSignatures()
    }

    @Test
    fun `only BNO should be able to start the flow`() {
        val bnoNode = bnoNodes.first()
        val participantNode = participantsNodes.first()

        //Populate with data
        runRequestMembershipFlow(bnoNode, participantNode)


        val membership = getMembership(participantNode, participantNode.identity(), bnoNode.identity())
        try {
            val future = participantNode.startFlow(SelfIssueMembershipFlow(membership))
            mockNetwork.runNetwork()
            future.getOrThrow()
            fail()
        } catch (e : BNONotWhitelisted) {
        }
    }


}
