package com.r3.businessnetworks.billing.flows.member

import com.r3.businessnetworks.billing.flows.bno.IssueBillingStateFlow
import com.r3.businessnetworks.billing.states.BillingChipState
import com.r3.businessnetworks.billing.states.BillingState
import com.r3.businessnetworks.testutilities.AbstractBusinessNetworksFlowTest
import com.r3.businessnetworks.testutilities.identity
import net.corda.core.node.services.queryBy
import org.junit.Test
import kotlin.test.assertEquals

class ChipOffBillingStateFlowTest : AbstractBusinessNetworksFlowTest(1, 1,
        listOf("com.r3.businessnetworks.billing.flows", "com.r3.businessnetworks.billing.states")) {

    private fun bnoNode() = bnoNodes.single()
    private fun participantNode() = participantsNodes.single()

    @Test
    fun `should chip off multiple states`() {
        val chipOffAmount = 2L
        val numberOfChips = 3

        runFlowAndReturn(bnoNode(), IssueBillingStateFlow(participantNode().identity(), 10L))
        val billingState = participantNode().services.vaultService.queryBy<BillingState>().states.single()
        runFlowAndReturn(participantNode(), ChipOffBillingStateFlow(billingState, chipOffAmount, numberOfChips))

        val chips = participantNode().services.vaultService.queryBy<BillingChipState>().states
        val newBillingState = participantNode().services.vaultService.queryBy<BillingState>().states.single()

        chips.forEach {
            assertEquals(bnoNode().identity(), it.state.data.issuer)
            assertEquals(participantNode().identity(), it.state.data.owner)
            assertEquals(chipOffAmount, it.state.data.amount)
            assertEquals(billingState.state.data.linearId, it.state.data.billingStateLinearId)
        }

        assertEquals(chips.size, numberOfChips)
        assertEquals(newBillingState.state.data.spent, chipOffAmount * numberOfChips)
    }
}