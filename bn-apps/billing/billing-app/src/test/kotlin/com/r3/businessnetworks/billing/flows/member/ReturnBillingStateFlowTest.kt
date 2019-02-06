package com.r3.businessnetworks.billing.flows.member

import com.r3.businessnetworks.billing.flows.bno.IssueBillingStateFlow
import com.r3.businessnetworks.billing.states.BillingChipState
import com.r3.businessnetworks.billing.states.BillingState
import com.r3.businessnetworks.billing.states.BillingStateStatus
import com.r3.businessnetworks.testutilities.AbstractBusinessNetworksFlowTest
import com.r3.businessnetworks.testutilities.identity
import net.corda.core.node.services.queryBy
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReturnBillingStateFlowTest : AbstractBusinessNetworksFlowTest(1, 1,
        listOf("com.r3.businessnetworks.billing.flows", "com.r3.businessnetworks.billing.states")) {
    private fun bnoNode() = bnoNodes.single()
    private fun participantNode() = participantsNodes.single()

    @Test
    fun `test return`() {
        runFlowAndReturn(bnoNode(), IssueBillingStateFlow(participantNode().identity(), 1000L, Instant.now()))
        val billingState = participantNode().services.vaultService.queryBy<BillingState>().states.single()
        runFlowAndReturn(participantNode(), ReturnBillingStateFlow(billingState))
        val billingStateAfterReturn = bnoNode().services.vaultService.queryBy<BillingState>().states.single()
        assertEquals(BillingStateStatus.RETURNED, billingStateAfterReturn.state.data.status)
    }

    @Test
    fun `test attach unspent chips and return`() {
        runFlowAndReturn(bnoNode(), IssueBillingStateFlow(participantNode().identity(), 10L))
        val billingState = participantNode().services.vaultService.queryBy<BillingState>().states.single()
        runFlowAndReturn(participantNode(), ChipOffBillingStateFlow(billingState, 2L, 3))
        val chippedOddBillingState = participantNode().services.vaultService.queryBy<BillingState>().states.single()
        // now we have 3 unspent chips
        runFlowAndReturn(participantNode(), AttachUnspentChipsAndReturnBillingStateFlow(chippedOddBillingState))
        val returnedBillingState = participantNode().services.vaultService.queryBy<BillingState>().states.single()
        val unspentChips = participantNode().services.vaultService.queryBy<BillingChipState>().states
        assertTrue(unspentChips.isEmpty())
        assertEquals(0L, returnedBillingState.state.data.spent)
    }
}