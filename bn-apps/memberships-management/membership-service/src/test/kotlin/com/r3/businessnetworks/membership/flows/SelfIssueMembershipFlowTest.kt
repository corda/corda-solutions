package com.r3.businessnetworks.membership.flows

import com.r3.businessnetworks.membership.states.MembershipContract
import net.corda.core.flows.FlowException
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class SelfIssueMembershipFlowTest : AbstractFlowTest(
        numberOfBusinessNetworks = 2,
        numberOfParticipants = 4,
        participantRespondingFlows = listOf(NotificationsCounterFlow::class.java)) {

    @Test
    fun `self issue happy path`() {
        val bnoNode = bnoNodes.first()
        //membership state before activation
        val stx = runSelfIssueMembershipFlow(bnoNode)
        val outputTxState = stx.tx.outputs.single()
        val command = stx.tx.commands.single()
        assertEquals(MembershipContract.CONTRACT_NAME, outputTxState.contract)
        assertTrue(command.value is MembershipContract.Commands.Activate)
        stx.verifyRequiredSignatures()
    }

    @Test
    fun `Flow should fail if membership already exists`() {
        val bnoNode = bnoNodes.first()

        runRequestMembershipFlow(bnoNode, bnoNode)
        assertThrows(FlowException::class.java) {
            runSelfIssueMembershipFlow(bnoNode)
        }
    }

    @Test
    fun `only BNO should be able to start the flow`() {
        val participantNode = participantsNodes.first()
        assertThrows(BNONotWhitelisted::class.java) {
            runSelfIssueMembershipFlow(participantNode)
        }
    }
}
