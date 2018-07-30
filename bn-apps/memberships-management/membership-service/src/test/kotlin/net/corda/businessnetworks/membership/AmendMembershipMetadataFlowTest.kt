package net.corda.businessnetworks.membership

import net.corda.businessnetworks.membership.bno.OnMembershipChanged
import net.corda.businessnetworks.membership.bno.service.BNOConfigurationService
import net.corda.businessnetworks.membership.states.Membership
import net.corda.businessnetworks.membership.states.MembershipMetadata
import net.corda.core.flows.FlowException
import org.junit.Test

class AmendMembershipMetadataFlowTest : AbstractFlowTest(2) {
    override fun registerFlows() {
        participantsNodes.forEach {
            it.registerInitiatedFlow(TestNotifyMembersFlowResponder::class.java)
        }
    }

    @Test
    fun `amend metadata should succeed`() {
        val memberNode = participantsNodes.first()
        val memberParty = identity(memberNode)

        runRequestMembershipFlow(memberNode)
        runActivateMembershipFlow(bnoNode, memberParty)

        val existingMembership = getMembership(memberNode, memberParty)
        val newMetadata = existingMembership.state.data.membershipMetadata.copy(role = "Some other role")

        val partiallySignedTx = runAmendMetadataFlow(memberNode, newMetadata)
        val allMemberTxs = allTransactions(memberNode)
        val allSignedTx = allMemberTxs.single { it.id ==  partiallySignedTx.id}
        allSignedTx.verifyRequiredSignatures()

        val outputWithContract = allSignedTx.tx.outputs.single()
        val outputMembership = outputWithContract.data as Membership.State
        val command = allSignedTx.tx.commands.single()

        assert(command.value is Membership.Commands.Amend)
        assert(outputWithContract.contract == Membership.CONTRACT_NAME)
        assert(outputMembership.membershipMetadata == newMetadata)
        assert(allSignedTx.inputs.single() == existingMembership.ref)

        val notifiedParty = TestNotifyMembersFlowResponder.NOTIFICATIONS.filter { it.second is OnMembershipChanged }.map { it.first }.single()
        assert(notifiedParty == memberParty)
    }


    @Test
    fun `no message should be sent if notifications are disabled`() {
        val bnoConfiguration = bnoNode.services.cordaService(BNOConfigurationService::class.java)
        bnoConfiguration.reloadPropertiesFromFile("membership-service-notifications-disabled.properties")

        val memberNode = participantsNodes.first()
        val memberParty = identity(memberNode)

        runRequestMembershipFlow(memberNode)
        runActivateMembershipFlow(bnoNode, memberParty)

        val existingMembership = getMembership(memberNode, memberParty)
        val newMetadata = existingMembership.state.data.membershipMetadata.copy(role = "Some other role")

        runAmendMetadataFlow(memberNode, newMetadata)

        assert(TestNotifyMembersFlowResponder.NOTIFICATIONS.isEmpty())
    }

    @Test
    fun `non members should be unable to trigger this flow`() {
        val memberNode = participantsNodes.first()
        val memberParty = identity(memberNode)

        runRequestMembershipFlow(memberNode)
        runActivateMembershipFlow(bnoNode, memberParty)

        try {
            runAmendMetadataFlow(memberNode, MembershipMetadata("Some role"))
        } catch (e : FlowException) {
            assert("$memberParty is not a member" == e.message)
        }
    }
}