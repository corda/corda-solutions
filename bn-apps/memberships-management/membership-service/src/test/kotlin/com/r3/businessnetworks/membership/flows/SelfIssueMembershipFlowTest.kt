package com.r3.businessnetworks.membership.flows

import com.r3.businessnetworks.membership.flows.bno.RequestMembershipFlowResponder
import com.r3.businessnetworks.membership.flows.bno.service.DatabaseService
import com.r3.businessnetworks.membership.flows.member.service.MemberConfigurationService
import com.r3.businessnetworks.membership.states.MembershipContract
import com.r3.businessnetworks.membership.states.MembershipState
import com.r3.businessnetworks.membership.testextensions.RequestMembershipFlowResponderWithMetadataVerification
import net.corda.core.flows.FlowException
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class SelfIssueMembershipFlowTest : AbstractFlowTest(numberOfBusinessNetworks = 2,
        numberOfParticipants = 2,
        participantRespondingFlows = listOf(RequestMembershipFlowResponder::class.java)) {

    @Test
    fun `self issue happy path`() {
        val bnoNode = bnoNodes.first()

        val stx = runSelfIssueMembershipFlow(bnoNode)
        
        assert(stx.notary!!.name == notaryName)

        val outputWithContract = stx.tx.outputs.single()
        val outputMembership = outputWithContract.data as MembershipState<*>
        val command = stx.tx.commands.single()

        assert(command.value is MembershipContract.Commands.Activate)
        assert(outputWithContract.contract == MembershipContract.CONTRACT_NAME)
        assert(outputMembership.bno == bnoNode.identity())
        assert(outputMembership.member == bnoNode.identity())
        stx.verifyRequiredSignatures()

        // no notifications should be sent at this point
        assertTrue(NotificationsCounterFlow.NOTIFICATIONS.isEmpty())
    }

    @Test
    fun `self issue should fail if a membership state already exists`() {
        val bnoNode = bnoNodes.first()

        runSelfIssueMembershipFlow(bnoNode)

        try {
            runSelfIssueMembershipFlow(bnoNode)
            fail()
        } catch (e : FlowException) {
            assert("Membership already exists" == e.message)
        }
    }
}