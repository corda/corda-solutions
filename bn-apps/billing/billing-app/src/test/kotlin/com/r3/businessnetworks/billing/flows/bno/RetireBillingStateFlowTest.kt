package com.r3.businessnetworks.billing.flows.bno

import com.r3.businessnetworks.billing.states.BillingState
import com.r3.businessnetworks.testutilities.AbstractBusinessNetworksFlowTest
import com.r3.businessnetworks.testutilities.identity
import net.corda.core.node.services.queryBy
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RetireBillingStateFlowTest : AbstractBusinessNetworksFlowTest(1, 1,
        listOf("com.r3.businessnetworks.billing.flows", "com.r3.businessnetworks.billing.states")) {
    private fun bnoNode() = bnoNodes.single()
    private fun participantNode() = participantsNodes.single()

    @Test
    fun `test Retire BillingState`() {
        runFlowAndReturn(bnoNode(), IssueBillingStateFlow(participantNode().identity(), 1000L, Instant.now()))
        val billingState = participantNode().services.vaultService.queryBy<BillingState>().states.single()
        runFlowAndReturn(bnoNode(), RetireBillingStateFlow(billingState))
        assertTrue(participantNode().services.vaultService.queryBy<BillingState>().states.isEmpty())
    }

    @Test
    fun `retire BillingState for party`() {
        runFlowAndReturn(bnoNode(), IssueBillingStateFlow(participantNode().identity(), 1000L, Instant.now()))
        runFlowAndReturn(bnoNode(), IssueBillingStateFlow(participantNode().identity(), 1000L, Instant.now()))
        assertEquals(2, participantNode().services.vaultService.queryBy<BillingState>().states.size)

        runFlowAndReturn(bnoNode(), RetireBillinStateForPartyFlow(participantNode().identity()))
        assertTrue(participantNode().services.vaultService.queryBy<BillingState>().states.isEmpty())
    }
}