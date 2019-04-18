package com.r3.businessnetworks.billing.flows.bno

import com.r3.businessnetworks.billing.states.BillingState
import com.r3.businessnetworks.testutilities.AbstractBusinessNetworksFlowTest
import com.r3.businessnetworks.testutilities.identity
import net.corda.core.contracts.StateAndRef
import net.corda.core.node.services.queryBy
import net.corda.testing.node.StartedMockNode
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals

class IssueBillingStateFlowTest : AbstractBusinessNetworksFlowTest(
        numberOfBusinessNetworks = 1,
        numberOfParticipants = 1,
        cordappPackages = listOf(
                "com.r3.businessnetworks.billing.flows",
                "com.r3.businessnetworks.billing.states")
) {

    private fun bnoNode() = bnoNodes.single()
    private fun participantNode() = participantsNodes.single()

    @Test
    fun `test issue`() {
        val amount = 100L
        val expiryDate = Instant.now()

        runFlowAndReturn(bnoNode(), IssueBillingStateFlow(participantNode().identity(), amount, expiryDate))

        val vaultBillingState = getBillingStates(participantNode()).single()
        assertEquals(amount, vaultBillingState.state.data.issued)
        assertEquals(participantNode().identity(), vaultBillingState.state.data.owner)
        assertEquals(bnoNode().identity(), vaultBillingState.state.data.issuer)
        assertEquals(expiryDate, vaultBillingState.state.data.expiryDate)
    }

    private fun getBillingStates(node : StartedMockNode) : List<StateAndRef<BillingState>> =
            node.services.vaultService.queryBy<BillingState>().states

}


