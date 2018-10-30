package net.corda.businessnetworks.membership

import net.corda.businessnetworks.membership.bno.OnMembershipChanged
import net.corda.businessnetworks.membership.states.MembershipContract
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class SuspendMembershipFlowTest : AbstractFlowTest(2) {

    override fun registerFlows() {
        participantsNodes.forEach {
            it.registerInitiatedFlow(NotificationsCounterFlow::class.java)
        }
    }

    private fun testMembershipSuspension(suspensionFunction : (memberParty : Party) -> SignedTransaction) {
        val suspendedMemberNode = participantsNodes.first()
        val activeMemberNode = participantsNodes[1]
        val suspendedMemberIdentity = identity(suspendedMemberNode)
        val activeMemberIdentity = identity(activeMemberNode)

        participantsNodes.forEach {
            runRequestMembershipFlow(it)
            runActivateMembershipFlow(bnoNode, identity(it))
        }

        // cleaning up notifications as we are interested in SUSPENDs only
        NotificationsCounterFlow.NOTIFICATIONS.clear()

        val inputMembership = getMembership(suspendedMemberNode, suspendedMemberIdentity)
        val stx = suspensionFunction(suspendedMemberIdentity)
        stx.verifyRequiredSignatures()

        val outputTxState = stx.tx.outputs.single()
        val command = stx.tx.commands.single()

        assert(MembershipContract.CONTRACT_NAME == outputTxState.contract)
        assert(command.value is MembershipContract.Commands.Suspend)
        assert(stx.inputs.single() == inputMembership.ref)

        // both the active and the suspended member should have received the same notification
        val suspendedMembership = getMembership(bnoNode, suspendedMemberIdentity)
        assertEquals(setOf(NotificationHolder(suspendedMemberIdentity, bnoParty, OnMembershipChanged(suspendedMembership)),
                NotificationHolder(activeMemberIdentity, bnoParty, OnMembershipChanged(suspendedMembership))), NotificationsCounterFlow.NOTIFICATIONS.toSet())

    }

    @Test
    fun `membership suspension happy path`() = testMembershipSuspension { memberParty -> runSuspendMembershipFlow(bnoNode, memberParty) }

    @Test
    fun `membership suspension should succeed when using a convenience flow`() = testMembershipSuspension { memberParty -> runSuspendMembershipForPartyFlow(bnoNode, memberParty) }

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