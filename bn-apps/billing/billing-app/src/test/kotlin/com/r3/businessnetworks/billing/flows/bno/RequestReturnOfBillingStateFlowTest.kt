package com.r3.businessnetworks.billing.flows.bno

import com.r3.businessnetworks.billing.flows.member.ChipOffBillingStateFlow
import com.r3.businessnetworks.billing.states.BillingState
import com.r3.businessnetworks.billing.states.BillingStateStatus
import com.r3.businessnetworks.testutilities.AbstractBusinessNetworksFlowTest
import com.r3.businessnetworks.testutilities.identity
import net.corda.core.node.services.queryBy
import org.junit.Test
import kotlin.test.assertEquals

class RequestReturnOfBillingStateFlowTest : AbstractBusinessNetworksFlowTest(1, 1,
        listOf("com.r3.businessnetworks.billing.flows", "com.r3.businessnetworks.billing.states")) {

    private fun bnoNode() = bnoNodes.single()
    private fun participantNode() = participantsNodes.single()

    @Test
    fun `happy path`() {
        runFlowAndReturn(bnoNode(), IssueBillingStateFlow(participantNode().identity(), 10L))
        val billingState = participantNode().services.vaultService.queryBy<BillingState>().states.single()
        runFlowAndReturn(participantNode(), ChipOffBillingStateFlow(billingState, 1L, 3))
        runFlowAndReturn(bnoNode(), RequestReturnOfBillingStateForPartyFlow(participantNode().identity()))
        val billingStateAfterReturn = participantNode().services.vaultService.queryBy<BillingState>().states.single()
        assertEquals(0L, billingStateAfterReturn.state.data.spent)
        assertEquals(BillingStateStatus.RETURNED, billingStateAfterReturn.state.data.status)
    }
}