package com.r3.businessnetworks.billing.demo.contracts

import com.r3.businessnetworks.billing.states.BillingChipState
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.contracts.requireThat
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import java.lang.IllegalArgumentException

class SampleContract : Contract {
    companion object {
        // amount of billing chips to be paid for each transaction by its participants
        const val BILLING_CHIPS_TO_PAY = 10L
        const val CONTRACT_NAME = "com.r3.businessnetworks.billing.demo.contracts.SampleContract"
    }

    sealed class Commands : CommandData, TypeOnlyCommandData() {
        class Issue : Commands()
        class Transfer : Commands()
    }

    override fun verify(tx : LedgerTransaction) {
        // there should be only one sample command
        val sampleCommand = tx.commandsOfType<Commands>().single()

        when (sampleCommand.value) {
            is Commands.Issue -> requireThat {
                "There should be no inputs of type SampleState" using (tx.inputsOfType<SampleState>().isEmpty())
                "There should be a single output of type SampleState" using (tx.outputsOfType<SampleState>().size == 1)
                val outputState = tx.outputsOfType<SampleState>().single()
                "Owner of the state should be a signer" using (sampleCommand.signers.single() == outputState.owner.owningKey)

                // Now we need to verify that the owner of the state has actually paid for the issuance
                verifyThatParticipantHasPaid(outputState.owner, tx)
            }
            is Commands.Transfer -> requireThat {
                "There should be one input of type SampleState" using (tx.inputsOfType<SampleState>().size == 1)
                "There should be a single output of type SampleState" using (tx.outputsOfType<SampleState>().size == 1)
                val inputState = tx.inputsOfType<SampleState>().single()
                val outputState = tx.outputsOfType<SampleState>().single()
                "Input and output states should have different owners" using (inputState.owner != outputState.owner)
                "Both of the states should be signers" using (sampleCommand.signers.toSet() == setOf(inputState.owner.owningKey, outputState.owner.owningKey))

                // verify that both of the parties have paid for transaction
                verifyThatParticipantHasPaid(inputState.owner, tx)
                verifyThatParticipantHasPaid(outputState.owner, tx)
            }
            else -> throw IllegalArgumentException("Unsupported command ${sampleCommand.value}")
        }
    }

    private fun verifyThatParticipantHasPaid(party : Party, tx : LedgerTransaction) {
        // filtering out a billing chip for this particular party. Assuming that each party can add only a single chip to transaction
        val billingChip = tx.inputsOfType<BillingChipState>().single { it.owner == party }
        requireThat {
            "Billing chip amount should be $BILLING_CHIPS_TO_PAY" using (billingChip.amount == BILLING_CHIPS_TO_PAY)
        }
    }
}

/**
 * A sample state that parties can issue to themselves and transfer to each other
 */
@BelongsToContract(SampleContract::class)
data class SampleState(val owner : Party) : ContractState {
    override val participants = listOf(owner)
}