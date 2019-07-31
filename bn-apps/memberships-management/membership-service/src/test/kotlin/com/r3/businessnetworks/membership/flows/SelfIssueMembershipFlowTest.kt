package com.r3.businessnetworks.membership.flows

import com.r3.businessnetworks.membership.flows.bno.SelfIssueMembershipFlow
import com.r3.businessnetworks.membership.flows.bno.service.DatabaseService
import com.r3.businessnetworks.membership.states.MembershipContract
import com.r3.businessnetworks.membership.states.MembershipState
import net.corda.core.flows.FlowException
import net.corda.core.utilities.getOrThrow
import org.junit.Test
import kotlin.test.fail

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

        assert(MembershipContract.CONTRACT_NAME == outputTxState.contract)
        assert(command.value is MembershipContract.Commands.Activate)
        stx.verifyRequiredSignatures()
    }

    @Test
    fun `Flow should fail if membership already exists`(){
        val bnoNode = bnoNodes.first()
        runRequestMembershipFlow(bnoNode, bnoNode)
        try {
            runSelfIssueMembershipFlow(bnoNode)
            fail()
        }catch (e : FlowException) {
        assert("Membership already exists" == e.message)
    }
    }

    @Test
    fun `only BNO should be able to start the flow`() {
        val participantNode = participantsNodes.first()
        try {
            runSelfIssueMembershipFlow(participantNode)
            fail()
        } catch (e : BNONotWhitelisted) {
        }
    }
}
