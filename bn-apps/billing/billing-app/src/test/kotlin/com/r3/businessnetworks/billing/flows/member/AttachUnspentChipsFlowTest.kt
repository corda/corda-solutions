package com.r3.businessnetworks.billing.flows.member

import com.r3.businessnetworks.billing.flows.bno.IssueBillingStateFlow
import com.r3.businessnetworks.billing.states.BillingChipState
import com.r3.businessnetworks.billing.states.BillingState
import com.r3.businessnetworks.testutilities.AbstractBusinessNetworksFlowTest
import com.r3.businessnetworks.testutilities.identity
import net.corda.core.contracts.StateAndRef
import net.corda.core.node.services.queryBy
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AttachUnspentChipsFlowTest : AbstractBusinessNetworksFlowTest(1, 1,
        listOf("com.r3.businessnetworks.billing.flows", "com.r3.businessnetworks.billing.states")) {

    private fun bnoNode() = bnoNodes.single()
    private fun participantNode() = participantsNodes.single()

    @Test
    fun `test attach unspent chips`() {
        val (billingState, billingStateAfterChipOff, chips) = issueSomeChips()
        runFlowAndReturn(participantNode(), AttachUnspentChipsFlow(billingStateAfterChipOff, chips))

        val billingStateAfterAttach = participantNode().services.vaultService.queryBy<BillingState>().states.single()
        val chipsAfterAttach = participantNode().services.vaultService.queryBy<BillingChipState>().states
        assertTrue(chipsAfterAttach.isEmpty())
        assertEquals(billingState.state.data, billingStateAfterAttach.state.data)
    }

    @Test
    fun `test attach all unspent chips`() {
        val (billingState, billingStateAfterChipOff, _) = issueSomeChips()
        runFlowAndReturn(participantNode(), AttachAllUnspentChipsFlow(billingStateAfterChipOff))

        val billingStateAfterAttach = participantNode().services.vaultService.queryBy<BillingState>().states.single()
        val chipsAfterAttach = participantNode().services.vaultService.queryBy<BillingChipState>().states
        assertTrue(chipsAfterAttach.isEmpty())
        assertEquals(billingState.state.data, billingStateAfterAttach.state.data)
    }

    private fun issueSomeChips() : Triple<StateAndRef<BillingState>, StateAndRef<BillingState>, List<StateAndRef<BillingChipState>>> {
        runFlowAndReturn(bnoNode(), IssueBillingStateFlow(participantNode().identity(), 10L))
        val billingState = participantNode().services.vaultService.queryBy<BillingState>().states.single()
        runFlowAndReturn(participantNode(), ChipOffBillingStateFlow(billingState, 1L, 5))
        return Triple(billingState,
                participantNode().services.vaultService.queryBy<BillingState>().states.single(),
                participantNode().services.vaultService.queryBy<BillingChipState>().states)
    }
}