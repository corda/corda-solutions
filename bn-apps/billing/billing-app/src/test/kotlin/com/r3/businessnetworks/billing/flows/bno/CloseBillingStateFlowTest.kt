package com.r3.businessnetworks.billing.flows.bno

import com.r3.businessnetworks.billing.states.BillingState
import com.r3.businessnetworks.billing.states.BillingStateStatus
import com.r3.businessnetworks.testutilities.AbstractBusinessNetworksFlowTest
import com.r3.businessnetworks.testutilities.identity
import net.corda.core.node.services.queryBy
import org.junit.Test
import kotlin.test.assertEquals

class CloseBillingStateFlowTest : AbstractBusinessNetworksFlowTest(1, 1,
        listOf("com.r3.businessnetworks.billing.flows", "com.r3.businessnetworks.billing.states")) {
    private fun bnoNode() = bnoNodes.single()
    private fun participantNode() = participantsNodes.first()

    @Test
    fun `test close billing state flow`() {
        // issuing and revoking billing state
        runFlowAndReturn(bnoNode(), IssueBillingStateFlow(participantNode().identity(), 10L))
        runFlowAndReturn(bnoNode(), RevokeBillingStatesForPartyFlow(participantNode().identity()))

        // closing the revoked state
        val revokedBillingState = bnoNode().services.vaultService.queryBy<BillingState>().states.single()
        runFlowAndReturn(bnoNode(), CloseBillingStateFlow(revokedBillingState))

        val closedBillingState = bnoNode().services.vaultService.queryBy<BillingState>().states.single()
        assertEquals(BillingStateStatus.CLOSED, closedBillingState.state.data.status)
    }
}