package com.r3.businessnetworks.billing.flows.bno

import com.r3.businessnetworks.billing.flows.member.ReturnBillingStateFlow
import com.r3.businessnetworks.billing.states.BillingState
import com.r3.businessnetworks.billing.states.BillingStateStatus
import com.r3.businessnetworks.testutilities.AbstractBusinessNetworksFlowTest
import com.r3.businessnetworks.testutilities.identity
import net.corda.core.node.services.queryBy
import org.junit.Test
import kotlin.test.assertEquals

class RevokeBillingStateFlowTest : AbstractBusinessNetworksFlowTest(1, 2,
        listOf("com.r3.businessnetworks.billing.flows", "com.r3.businessnetworks.billing.states")) {
    private fun bnoNode() = bnoNodes.single()
    private fun participantNode() = participantsNodes.first()
    private fun participant2Node() = participantsNodes[1]

    @Test
    fun `test revoke billing state flow`() {
        runFlowAndReturn(bnoNode(), IssueBillingStateFlow(participantNode().identity(), 10L))
        val billingState = bnoNode().services.vaultService.queryBy<BillingState>().states.single()
        runFlowAndReturn(bnoNode(), RevokeBillingStateFlow(billingState))
        val revokedBillingState = bnoNode().services.vaultService.queryBy<BillingState>().states.single()
        assertEquals(BillingStateStatus.REVOKED, revokedBillingState.state.data.status)
    }

    @Test
    fun `test revoke billing state for party flow`() {
        // issuing 3 states to participant 1 and 1 state to participant 2
        runFlowAndReturn(bnoNode(), IssueBillingStateFlow(participantNode().identity(), 10L))
        runFlowAndReturn(bnoNode(), IssueBillingStateFlow(participantNode().identity(), 10L))
        runFlowAndReturn(bnoNode(), IssueBillingStateFlow(participantNode().identity(), 10L))
        runFlowAndReturn(bnoNode(), IssueBillingStateFlow(participant2Node().identity(), 10L))

        // returning one of the participant 1's states
        val billingStateToReturn = participantNode().services.vaultService.queryBy<BillingState>().states.first()
        runFlowAndReturn(participantNode(), ReturnBillingStateFlow(billingStateToReturn))

        // verifying that only 2 active participant 1's states are revoked. Returned state and participant 2's state should remain untouched
        runFlowAndReturn(bnoNode(), RevokeBillingStatesForPartyFlow(participantNode().identity()))

        val participant1States = participantNode().services.vaultService.queryBy<BillingState>().states.map { it.state.data }
        val participant2State = participant2Node().services.vaultService.queryBy<BillingState>().states.single().state.data

        assertEquals(BillingStateStatus.RETURNED, participant1States.single { it.linearId == billingStateToReturn.state.data.linearId }.status)
        assertEquals(BillingStateStatus.REVOKED, participant1States.filter { it.linearId != billingStateToReturn.state.data.linearId }.map { it.status }.toSet().single())
        assertEquals(BillingStateStatus.ACTIVE, participant2State.status)
    }
}