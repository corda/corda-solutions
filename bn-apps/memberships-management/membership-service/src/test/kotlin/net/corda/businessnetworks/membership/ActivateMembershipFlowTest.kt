package net.corda.businessnetworks.membership

import net.corda.businessnetworks.membership.bno.OnMembershipActivated
import net.corda.businessnetworks.membership.bno.service.BNOConfigurationService
import net.corda.businessnetworks.membership.states.MembershipContract
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class ActivateMembershipFlowTest : AbstractFlowTest(2) {

    override fun registerFlows() {
        participantsNodes.forEach { it.registerInitiatedFlow(TestNotifyMembersFlowResponder::class.java) }
    }

    @Before
    override fun setup() {
        super.setup()
        // This is ugly, but there is no other way to check whether the responding flow was actually triggered
        TestNotifyMembersFlowResponder.NOTIFICATIONS.clear()
    }

    @After
    override fun tearDown() {
        super.tearDown()
        TestNotifyMembersFlowResponder.NOTIFICATIONS.clear() //if we don't do that it can interfere with tests in other classes
    }

    @Test
    fun `membership activation should succeed`() {
        val memberNode = participantsNodes.first()
        val memberParty = identity(memberNode)

        runRequestMembershipFlow(memberNode)

        // membership state before activation
        val inputMembership = getMembership(memberNode, memberParty)

        val stx = runActivateMembershipFlow(bnoNode, memberParty)
        stx.verifyRequiredSignatures()

        val outputTxState = stx.tx.outputs.single()
        val command = stx.tx.commands.single()

        assert(MembershipContract.CONTRACT_NAME == outputTxState.contract)
        assert(command.value is MembershipContract.Commands.Activate)
        assert(stx.tx.inputs.single() == inputMembership.ref)

        val notification = TestNotifyMembersFlowResponder.NOTIFICATIONS.single()
        assert(notification.first == memberParty)
        assert(notification.second is OnMembershipActivated)
    }

    @Test
    fun `membership activation should succeed when using convenience flow`() {
        val memberNode = participantsNodes.first()
        val memberParty = identity(memberNode)

        runRequestMembershipFlow(memberNode)

        // membership state before activation
        val inputMembership = getMembership(memberNode, memberParty)

        val stx = runActivateMembershipForPartyFlow(bnoNode, memberParty)
        stx.verifyRequiredSignatures()

        val outputTxState = stx.tx.outputs.single()
        val command = stx.tx.commands.single()

        assert(MembershipContract.CONTRACT_NAME == outputTxState.contract)
        assert(command.value is MembershipContract.Commands.Activate)
        assert(stx.tx.inputs.single() == inputMembership.ref)

        val notification = TestNotifyMembersFlowResponder.NOTIFICATIONS.single()
        assert(notification.first == memberParty)
        assert(notification.second is OnMembershipActivated)
    }


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


        val notification = TestNotifyMembersFlowResponder.NOTIFICATIONS.single()
        assert(notification.first == memberParty)
        assert(notification.second is OnMembershipActivated)
    }
}