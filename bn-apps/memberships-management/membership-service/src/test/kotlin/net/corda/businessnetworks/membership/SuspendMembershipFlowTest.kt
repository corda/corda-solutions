package net.corda.businessnetworks.membership

import net.corda.businessnetworks.membership.bno.OnMembershipSuspended
import net.corda.businessnetworks.membership.bno.service.BNOConfigurationService
import net.corda.businessnetworks.membership.states.MembershipContract
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class SuspendMembershipFlowTest : AbstractFlowTest(2) {

    override fun registerFlows() {
        participantsNodes.forEach {
            it.registerInitiatedFlow(TestNotifyMembersFlowResponder::class.java)
        }
    }

    @Before
    override fun setup() {
        super.setup()
        // This is ugly, but there is no other way to check whether the responding flow was actually triggered
        TestNotifyMembersFlowResponder.NOTIFICATIONS.clear()
    }

    @Test
    fun `membership suspension should succeed`() {
        val memberNode = participantsNodes.first()
        val memberParty = identity(memberNode)

        participantsNodes.forEach {
            runRequestMembershipFlow(it)
            runActivateMembershipFlow(bnoNode, identity(it))
        }

        val inputMembership = getMembership(memberNode, memberParty)
        val stx = runSuspendMembershipFlow(bnoNode, memberParty)
        stx.verifyRequiredSignatures()

        val outputTxState = stx.tx.outputs.single()
        val command = stx.tx.commands.single()

        assert(MembershipContract.CONTRACT_NAME == outputTxState.contract)
        assert(command.value is MembershipContract.Commands.Suspend)
        assert(stx.inputs.single() == inputMembership.ref)

        val notifiedParties = TestNotifyMembersFlowResponder.NOTIFICATIONS.filter { it.second is OnMembershipSuspended }.map { it.first }
        assert(notifiedParties.toSet() == participantsNodes.map { identity(it) }.toSet())
    }

    @Test
    fun `membership suspension should succeed when using convenience flow`() {
        val memberNode = participantsNodes.first()
        val memberParty = identity(memberNode)

        participantsNodes.forEach {
            runRequestMembershipFlow(it)
            runActivateMembershipFlow(bnoNode, identity(it))
        }

        val inputMembership = getMembership(memberNode, memberParty)
        val stx = runSuspendMembershipForPartyFlow(bnoNode, memberParty)
        stx.verifyRequiredSignatures()

        val outputTxState = stx.tx.outputs.single()
        val command = stx.tx.commands.single()

        assert(MembershipContract.CONTRACT_NAME == outputTxState.contract)
        assert(command.value is MembershipContract.Commands.Suspend)
        assert(stx.inputs.single() == inputMembership.ref)

        val notifiedParties = TestNotifyMembersFlowResponder.NOTIFICATIONS.filter { it.second is OnMembershipSuspended }.map { it.first }
        assert(notifiedParties.toSet() == participantsNodes.map { identity(it) }.toSet())
    }

    @Test
    fun `only BNO should be able to start the flow`() {
        val memberNode = participantsNodes.first()
        val memberParty = identity(memberNode)

        runRequestMembershipFlow(memberNode)
        try {
            runSuspendMembershipFlow(memberNode, memberParty)
            fail()
        } catch (e : NotBNOException) {
            assertEquals("This node is not the business network operator of this membership", e.message)
        }
    }
}