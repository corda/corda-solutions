package com.r3.businessnetworks.billing.flows.member

import com.r3.businessnetworks.billing.flows.bno.IssueBillingStateFlow
import com.r3.businessnetworks.billing.states.BillingState
import com.r3.businessnetworks.testutilities.AbstractBusinessNetworksFlowTest
import com.r3.businessnetworks.testutilities.identity
import net.corda.core.node.services.queryBy
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals

class DataFlowsTest : AbstractBusinessNetworksFlowTest(2, 1,
        listOf("com.r3.businessnetworks.billing.flows", "com.r3.businessnetworks.billing.states")) {
    private fun bno1Node() = bnoNodes[0]
    private fun bno2Node() = bnoNodes[1]
    private fun participantNode() = participantsNodes.single()

    @Test
    fun `test get billing states by owner`() {
        val (participant1State1, _) = runFlowAndReturn(bno1Node(), IssueBillingStateFlow(participantNode().identity(), 1L, Instant.now()))
        val (participant1State2, _) = runFlowAndReturn(bno1Node(), IssueBillingStateFlow(participantNode().identity(), 2L, Instant.now()))
        val (_, _) = runFlowAndReturn(bno2Node(), IssueBillingStateFlow(participantNode().identity(), 2L, Instant.now()))

        val states = runFlowAndReturn(participantNode(), GetBillingStatesByIssuerFlow(bno1Node().identity()))

        assertEquals(setOf(participant1State1, participant1State2), states.map { it.state.data }.toSet())
    }

    @Test
    fun `test get billing states by linearId`() {
        val (state, _) = runFlowAndReturn(bno1Node(), IssueBillingStateFlow(participantNode().identity(), 1L, Instant.now()))
        val stateFromFlow = runFlowAndReturn(participantNode(), GetBillingStateByLinearId(state.linearId))!!
        assertEquals(state, stateFromFlow.state.data)
    }

    @Test
    fun `test get billing chip states by billingStateLinearId`() {
        runFlowAndReturn(bno1Node(), IssueBillingStateFlow(participantNode().identity(), 10L, Instant.now()))
        val billingState = participantNode().services.vaultService.queryBy<BillingState>().states.single()
        runFlowAndReturn(participantNode(), ChipOffBillingStateFlow(billingState, 1L, 5))
        val chips = runFlowAndReturn(participantNode(), GetChipsByBillingState(billingState.state.data.linearId))
        assertEquals(5, chips.size)
    }
}