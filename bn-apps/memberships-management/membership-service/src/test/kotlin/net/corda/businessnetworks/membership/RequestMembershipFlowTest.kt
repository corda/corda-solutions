package net.corda.businessnetworks.membership

import net.corda.businessnetworks.membership.bno.RequestMembershipFlowResponder
import net.corda.businessnetworks.membership.bno.service.DatabaseService
import net.corda.businessnetworks.membership.states.MembershipContract
import net.corda.businessnetworks.membership.states.MembershipState
import net.corda.core.flows.FlowException
import org.junit.Test
import kotlin.test.fail

class RequestMembershipFlowTest : AbstractFlowTest(2) {
    override fun registerFlows() {
        bnoNode.registerInitiatedFlow(RequestMembershipFlowResponder::class.java)
    }

    @Test
    fun `membership request should succeed`() {
        val memberNode = participantsNodes.first()
        val memberParty = identity(memberNode)

        val stx = runRequestMembershipFlow(memberNode)

        assert(stx.tx.inputs.isEmpty())
        assert(stx.notary!!.name == notaryName)

        val outputWithContract = stx.tx.outputs.single()
        val outputMembership = outputWithContract.data as MembershipState<*>
        val command = stx.tx.commands.single()

        assert(command.value is MembershipContract.Commands.Request)
        assert(outputWithContract.contract == MembershipContract.CONTRACT_NAME)
        assert(outputMembership.bno == bnoParty)
        assert(outputMembership.member == memberParty)

        stx.verifyRequiredSignatures()
    }

    @Test
    fun `membership request should fail if a membership state already exists`() {
        val memberNode = participantsNodes.first()

        runRequestMembershipFlow(memberNode)
        try {
            runRequestMembershipFlow(memberNode)
            fail()
        } catch (e : FlowException) {
            assert("Membership already exists" == e.message)
        }
    }

    @Test
    fun `membership request should fail if another membership request is already in progress`() {
        val memberNode = participantsNodes.first()
        val memberParty = identity(memberNode)
        val databaseService = bnoNode.services.cordaService(DatabaseService::class.java)

        bnoNode.transaction {
            databaseService.createPendingMembershipRequest(memberParty)
        }

        try {
            runRequestMembershipFlow(memberNode)
            fail()
        } catch (e : FlowException) {
            assert("Membership request already exists" == e.message)
        }

        bnoNode.transaction {
            databaseService.deletePendingMembershipRequest(memberParty)
        }
    }
}