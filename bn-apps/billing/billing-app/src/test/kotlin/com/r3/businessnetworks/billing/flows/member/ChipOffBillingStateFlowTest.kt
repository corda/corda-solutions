package com.r3.businessnetworks.billing.flows.member

import com.r3.businessnetworks.billing.flows.bno.IssueBillingStateFlow
import com.r3.businessnetworks.billing.states.BillingChipState
import com.r3.businessnetworks.billing.states.BillingState
import com.r3.businessnetworks.testutilities.AbstractBusinessNetworksFlowTest
import com.r3.businessnetworks.testutilities.identity
import net.corda.core.contracts.StateAndRef
import net.corda.core.node.services.queryBy
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals

class ChipOffBillingStateFlowTest : AbstractBusinessNetworksFlowTest(1, 1,
        listOf("com.r3.businessnetworks.billing.flows", "com.r3.businessnetworks.billing.states")) {

    private fun bnoNode() = bnoNodes.single()
    private fun participantNode() = participantsNodes.single()

    private fun genericTest(billingStateAmount : Long, expiryDate : Instant? = null, chipOffFunction : (billingState : StateAndRef<BillingState>) -> Pair<Long, Int>) {

        runFlowAndReturn(bnoNode(), IssueBillingStateFlow(participantNode().identity(), billingStateAmount, expiryDate))
        val billingState = participantNode().services.vaultService.queryBy<BillingState>().states.single()

        val (chipOffAmount, numberOfChips) = chipOffFunction(billingState)

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

    @Test
    fun `should chip off multiple states`()  = genericTest(10L) {billingState ->
        runFlowAndReturn(participantNode(), ChipOffBillingStateFlow(billingState, 2L, 3))
        Pair(2L, 3)
    }

    @Test
    fun `should chip off multiple states with time window`()  = genericTest(10L, Instant.now().plusSeconds(10000)) { billingState ->
        runFlowAndReturn(participantNode(), ChipOffBillingStateFlow(billingState, 2L, 3))
        Pair(2L, 3)
    }

    @Test
    fun `should chip off all available amount`() {
        genericTest(10L) { billingState ->
            runFlowAndReturn(participantNode(), ChipOffRemainingAmountFlow(billingState, 3L))
            Pair(3L, 3)
        }
        val billingStateAfterChipOff = participantNode().services.vaultService.queryBy<BillingState>().states.single()
        assertEquals(9, billingStateAfterChipOff.state.data.spent)
    }
}