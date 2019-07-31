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

        //to check if updated status BNO can still suspend memberships
//        runRequestAndActivateMembershipFlow(bnoNode, participantNode)
//        val stx2 = runSuspendMembershipForPartyFlow(bnoNode, participantNode.identity())
//        val Status = stx2.tx.commands.single()
//        assert(Status.value is MembershipContract.Commands.Suspend)
    }

    @Test
    fun `Flow should fail if membership already exists`(){
        val bnoNode = bnoNodes.first()
        runRequestMembershipFlow(bnoNode, bnoNode)
        try {
            SelfIssueMembershipFlow(bnoNode)
            fail()
        }catch (e : FlowException) {
        assert("Membership already exists" == e.message)
    }
    }

    @Test
    fun `only BNO should be able to start the flow`() {
        val bnoNode = bnoNodes.first()
        val participantNode = participantsNodes.first()

        val membership = getMembership(participantNode, participantNode.identity(), bnoNode.identity())
        try {
            val future = participantNode.startFlow(SelfIssueMembershipFlow(membership.state.data))
            mockNetwork.runNetwork()
            future.getOrThrow()
            fail()
        } catch (e : BNONotWhitelisted) {
        }
    }
}
