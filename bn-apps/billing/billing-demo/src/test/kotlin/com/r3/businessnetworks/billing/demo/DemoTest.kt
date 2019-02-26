package com.r3.businessnetworks.billing.demo

import com.r3.businessnetworks.billing.demo.contracts.SampleContract
import com.r3.businessnetworks.billing.demo.contracts.SampleState
import com.r3.businessnetworks.billing.demo.flows.IssueSampleStateFlow
import com.r3.businessnetworks.billing.demo.flows.TransferSampleStateFlow
import com.r3.businessnetworks.billing.flows.bno.IssueBillingStateFlow
import com.r3.businessnetworks.testutilities.AbstractBusinessNetworksFlowTest
import com.r3.businessnetworks.testutilities.identity
import net.corda.core.node.services.queryBy
import org.junit.Test

class DemoTest : AbstractBusinessNetworksFlowTest(1, 2,
        listOf("com.r3.businessnetworks.billing.demo.contracts",
                "com.r3.businessnetworks.billing.demo.flows",
                "com.r3.businessnetworks.billing.flows",
                "com.r3.businessnetworks.billing.states")){

    private fun bnoNode() = bnoNodes.single()
    private fun participant1Node() = participantsNodes[0]
    private fun participant2Node() = participantsNodes[1]

    @Test
    fun `test happy path`() {
        // issuing billing chips to both parties
        // issuer requires more tokens as they need to pay twice
        runFlowAndReturn(bnoNode(), IssueBillingStateFlow(participant1Node().identity(), 2 * SampleContract.BILLING_CHIPS_TO_PAY))
        runFlowAndReturn(bnoNode(), IssueBillingStateFlow(participant2Node().identity(), SampleContract.BILLING_CHIPS_TO_PAY))

        // Issuing a SampleState
        runFlowAndReturn(participant1Node(), IssueSampleStateFlow())

        // transferring SampleState between parties
        val sampleState = participant1Node().services.vaultService.queryBy<SampleState>().states.single()
        runFlowAndReturn(participant1Node(), TransferSampleStateFlow(sampleState, participant2Node().identity()))
    }
}