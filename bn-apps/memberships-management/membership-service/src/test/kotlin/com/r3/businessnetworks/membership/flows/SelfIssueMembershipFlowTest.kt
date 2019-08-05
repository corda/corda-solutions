package com.r3.businessnetworks.membership.flows

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isA
import com.natpryce.hamkrest.throws
import com.r3.businessnetworks.membership.states.MembershipContract
import net.corda.core.flows.FlowException
import org.junit.Test


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
        assertThat(MembershipContract.CONTRACT_NAME, equalTo(outputTxState.contract))
        assertThat(command.value, isA<MembershipContract.Commands.Activate>())
        stx.verifyRequiredSignatures()
    }

    @Test
    fun `Flow should fail if membership already exists`(){
        val bnoNode = bnoNodes.first()

        runRequestMembershipFlow(bnoNode, bnoNode)
        assertThat({ runSelfIssueMembershipFlow(bnoNode) }, throws<FlowException>())
    }

    @Test
    fun `only BNO should be able to start the flow`() {
        val participantNode = participantsNodes.first()
        assertThat({ runSelfIssueMembershipFlow(participantNode) }, throws<BNONotWhitelisted>())
    }
}
