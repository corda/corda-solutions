package com.r3.businessnetworks.membership

import com.r3.businessnetworks.membership.bno.RequestMembershipFlowResponder
import com.r3.businessnetworks.membership.bno.service.BNOConfigurationService
import com.r3.businessnetworks.membership.bno.service.DatabaseService
import com.r3.businessnetworks.membership.member.service.MemberConfigurationService
import com.r3.businessnetworks.membership.states.MembershipContract
import com.r3.businessnetworks.membership.states.MembershipState
import net.corda.core.contracts.Contract
import net.corda.core.flows.FlowException
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class RequestMembershipFlowTest : AbstractFlowTest(numberOfBusinessNetworks = 2,
        numberOfParticipants = 2,
        participantRespondingFlows = listOf(RequestMembershipFlowResponder::class.java)) {

    @Test
    fun `membership request happy path`() {
        val bnoNode = bnoNodes.first()
        val participantNode = participantsNodes.first()

        val stx = runRequestMembershipFlow(bnoNode, participantNode)

        assert(stx.tx.inputs.isEmpty())
        assert(stx.notary!!.name == notaryName)

        val outputWithContract = stx.tx.outputs.single()
        val outputMembership = outputWithContract.data as MembershipState<*>
        val command = stx.tx.commands.single()

        assert(command.value is MembershipContract.Commands.Request)
        assert(outputWithContract.contract == MembershipContract.CONTRACT_NAME)
        assert(outputMembership.bno == bnoNode.identity())
        assert(outputMembership.member == participantNode.identity())
        stx.verifyRequiredSignatures()

        // no notifications should be sent at this point
        assertTrue(NotificationsCounterFlow.NOTIFICATIONS.isEmpty())
    }

    @Test
    fun `membership request should fail if a membership state already exists`() {
        val bnoNode = bnoNodes.first()
        val participantNode = participantsNodes.first()

        runRequestMembershipFlow(bnoNode, participantNode)
        try {
            runRequestMembershipFlow(bnoNode, participantNode)
            fail()
        } catch (e : FlowException) {
            assert("Membership already exists" == e.message)
        }
    }

    @Test
    fun `membership request should fail if another membership request form the same member is already in progress`() {
        val bnoNode = bnoNodes.first()
        val participantNode = participantsNodes.first()
        val databaseService = bnoNode.services.cordaService(DatabaseService::class.java)

        bnoNode.transaction {
            databaseService.createPendingMembershipRequest(participantNode.identity())
        }

        try {
            runRequestMembershipFlow(bnoNode, participantNode)
            fail()
        } catch (e : FlowException) {
            assert("Membership request already exists" == e.message)
        }

        bnoNode.transaction {
            databaseService.deletePendingMembershipRequest(participantNode.identity())
        }
    }

    @Test
    fun `membership transaction can be verified by a custom contract`() {
        val bnoNode = bnoNodes.first()
        val memberNode = participantsNodes.first()

        // reloading configurations for both the member and the bno
        bnoNode.services.cordaService(BNOConfigurationService::class.java).reloadConfigurationFromFile(fileFromClasspath("membership-service-with-custom-contract.conf"))
        memberNode.services.cordaService(MemberConfigurationService::class.java).reloadConfigurationFromFile(fileFromClasspath("membership-service-with-custom-contract.conf"))

        val stx = runRequestMembershipFlow(bnoNode, memberNode)

        val outputWithContract = stx.tx.outputs.single()

        assert(outputWithContract.contract == "com.r3.businessnetworks.membership.DummyMembershipContract")
    }

    @Test(expected = BNONotWhitelisted::class)
    fun `the flow can be run only against whitelisted BNOs`() {
        val bnoNode = bnoNodes.first()
        val participantNode = participantsNodes.first()
        participantNode.services.cordaService(MemberConfigurationService::class.java).reloadConfigurationFromFile(fileFromClasspath("membership-service-without-bno-whitelist.conf"))

        runRequestMembershipFlow(bnoNode, participantNode)
    }

    @Test
    fun `request membership transaction should be rejected if BNO specifies a wrong contract`() {
        val bnoNode = bnoNodes.first()
        val participantNode = participantsNodes.first()

        // reloading configuration with a fake contract name
        participantNode.services.cordaService(MemberConfigurationService::class.java).reloadConfigurationFromFile(fileFromClasspath("membership-service-with-fake-contract-name.conf"))

        try {
            runRequestMembershipFlow(bnoNode, participantNode)
            fail()
        } catch (ex : FlowException) {
            assertEquals("Membership transactions have to be verified with not.existing.contract.Class contract", ex.message)
        }
    }
}

class DummyMembershipContract : Contract, MembershipContract() {
    override fun contractName() = "com.r3.businessnetworks.membership.DummyMembershipContract"
}