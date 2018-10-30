package net.corda.businessnetworks.membership

import net.corda.businessnetworks.membership.bno.OnMembershipChanged
import net.corda.businessnetworks.membership.bno.service.BNOConfigurationService
import net.corda.businessnetworks.membership.states.MembershipContract
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class ActivateMembershipFlowTest : AbstractFlowTest(2) {
    override fun registerFlows() {
        participantsNodes.forEach { it.registerInitiatedFlow(NotificationsCounterFlow::class.java) }
    }

    private fun testMembershipActivation(activationFunction : (member : Party) -> SignedTransaction) {
        val memberNode = participantsNodes.first()
        val memberParty = identity(memberNode)

        runRequestMembershipFlow(memberNode)

        // membership state before activation
        val inputMembership = getMembership(memberNode, memberParty)

        val stx = activationFunction(identity(memberNode))
        stx.verifyRequiredSignatures()

        val outputTxState = stx.tx.outputs.single()
        val command = stx.tx.commands.single()

        assert(MembershipContract.CONTRACT_NAME == outputTxState.contract)
        assert(command.value is MembershipContract.Commands.Activate)
        assert(stx.tx.inputs.single() == inputMembership.ref)

        // making sure that a correct notification has been send
        val membershipStateAndRef = getMembership(bnoNode, memberParty)
        val notification = NotificationsCounterFlow.NOTIFICATIONS.single()
        assertEquals(NotificationHolder(memberParty, bnoParty, OnMembershipChanged(membershipStateAndRef)), notification)
    }

    @Test
    fun `membership activation happy path`() = testMembershipActivation { member -> runActivateMembershipFlow(bnoNode, member) }

    @Test
    fun `membership activation should succeed when using a convenience flow`() = testMembershipActivation { member -> runActivateMembershipForPartyFlow(bnoNode, member) }

    @Test
    fun `only BNO should be able to start the flow`() {
        val memberNode = participantsNodes.first()
        val memberParty = identity(memberNode)

        runRequestMembershipFlow(memberNode)
        try {
            runActivateMembershipFlow(memberNode, memberParty)
            fail()
        } catch (e : NotBNOException) {
            assertEquals("This node is not the business network operator of this membership", e.message)
        }
    }

    @Test
    fun `membership can be auto activated`() {
        val bnoConfiguration = bnoNode.services.cordaService(BNOConfigurationService::class.java)
        bnoConfiguration.reloadPropertiesFromFile(fileFromClasspath("membership-service-with-auto-approver.conf"))
        val memberNode = participantsNodes.first()
        val memberParty = identity(memberNode)

        runRequestMembershipFlow(memberNode)

        // membership state before activation
        val inputMembership = getMembership(memberNode, memberParty)
        assertTrue(inputMembership.state.data.isActive())

        val updatedMembership = getMembership(bnoNode, memberParty)
        assertEquals(NotificationHolder(memberParty, bnoParty, OnMembershipChanged(updatedMembership)), NotificationsCounterFlow.NOTIFICATIONS.single())
    }
}