package net.corda.businessnetworks.membership

import net.corda.businessnetworks.membership.bno.OnMembershipRevoked
import net.corda.businessnetworks.membership.bno.service.BNOConfigurationService
import net.corda.businessnetworks.membership.states.Membership
import net.corda.businessnetworks.membership.testflow.TestNotifyMembersFlowResponder
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class RevokeMembershipFlowTest : AbstractFlowTest(2) {

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
    fun `membership revocation should succeed`() {
        val memberNode = participantsNodes.first()
        val memberParty = identity(memberNode)

        participantsNodes.forEach {
            runRequestMembershipFlow(it)
            runActivateMembershipFlow(bnoNode, identity(it))
        }

        val inputMembership = getMembership(memberNode, memberParty)
        val stx = runRevokeMembershipFlow(bnoNode, memberParty)
        stx.verifyRequiredSignatures()

        val outputTxState = stx.tx.outputs.single()
        val command = stx.tx.commands.single()

        assert(Membership.CONTRACT_NAME == outputTxState.contract)
        assert(command.value is Membership.Commands.Revoke)
        assert(stx.inputs.single() == inputMembership.ref)

        val notifiedParties = TestNotifyMembersFlowResponder.NOTIFICATIONS.filter { it.second is OnMembershipRevoked }.map { it.first }
        assert(notifiedParties.toSet() == participantsNodes.map { identity(it) }.toSet())
    }

    @Test
    fun `membership revocation should succeed when using convenience flow`() {
        val memberNode = participantsNodes.first()
        val memberParty = identity(memberNode)

        participantsNodes.forEach {
            runRequestMembershipFlow(it)
            runActivateMembershipFlow(bnoNode, identity(it))
        }

        val inputMembership = getMembership(memberNode, memberParty)
        val stx = runRevokeMembershipForPartyFlow(bnoNode, memberParty)
        stx.verifyRequiredSignatures()

        val outputTxState = stx.tx.outputs.single()
        val command = stx.tx.commands.single()

        assert(Membership.CONTRACT_NAME == outputTxState.contract)
        assert(command.value is Membership.Commands.Revoke)
        assert(stx.inputs.single() == inputMembership.ref)

        val notifiedParties = TestNotifyMembersFlowResponder.NOTIFICATIONS.filter { it.second is OnMembershipRevoked }.map { it.first }
        assert(notifiedParties.toSet() == participantsNodes.map { identity(it) }.toSet())
    }

    @Test
    fun `only BNO should be able to start the flow`() {
        val memberNode = participantsNodes.first()
        val memberParty = identity(memberNode)

        runRequestMembershipFlow(memberNode)
        try {
            runRevokeMembershipFlow(memberNode, memberParty)
            fail()
        } catch (e : NotBNOException) {
            assertEquals("This node is not the business network operator of this membership", e.message)
        }
    }


    @Test
    fun `no message should be sent if notifications are disabled`() {
        val bnoConfiguration = bnoNode.services.cordaService(BNOConfigurationService::class.java)
        bnoConfiguration.reloadPropertiesFromFile("membership-service-notifications-disabled.properties")

        val memberNode = participantsNodes.first()
        val memberParty = identity(memberNode)

        participantsNodes.forEach {
            runRequestMembershipFlow(it)
        }
        runRevokeMembershipFlow(bnoNode, memberParty)

        assert(TestNotifyMembersFlowResponder.NOTIFICATIONS.isEmpty())
    }
}