package com.r3.businessnetworks.billing.states

import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import java.lang.IllegalArgumentException
import java.time.Instant

class BillingContract : Contract {
    companion object {
        val CONTRACT_NAME = "com.r3.businessnetworks.billing.states.BillingContract"
    }

    interface Commands : CommandData {
        class Issue : Commands, TypeOnlyCommandData()
        class ChipOff : Commands, TypeOnlyCommandData()
        class Spend : Commands, TypeOnlyCommandData()
        class Retire : Commands, TypeOnlyCommandData()
    }

    override fun verify(tx : LedgerTransaction) {
        val command = tx.commands.requireSingleCommand(Commands::class.java)
        when (command.value) {
            is Commands.Issue -> requireThat {
                "There should be no input billing states" using (tx.inputsOfType<BillingState>().isEmpty() && tx.inputsOfType<BillingChipState>().isEmpty())
                "There should be no BillingChipStates in outputs" using (tx.outputsOfType<BillingChipState>().isEmpty())
                val billingState = tx.outputsOfType<BillingState>().single()
                "Both owner and issuer should be signers" using (command.signers.toSet() == setOf(billingState.issuer.owningKey, billingState.owner.owningKey))
                "Issued and spent amounts should not be negative" using (billingState.spent >= 0L && billingState.issued >= 0L)
                // post paid scheme
                if (billingState.issued == 0L) {
                    "Issued and spent should be equal to zero for post paid billing" using (billingState.issued == 0L && billingState.spent == 0L)
                } else {
                    "Spent should be equal to zero for pre paid billing" using (billingState.spent == 0L)
                }
            }
            is Commands.ChipOff -> requireThat {
                "There should be no inputs of type BillingChipState in ChipOff transaction" using (tx.inputsOfType<BillingChipState>().isEmpty())
                val inputBillingState = tx.inputsOfType<BillingState>().single()
                val outputBillingState = tx.outputsOfType<BillingState>().single()
                val outputChipState = tx.outputsOfType<BillingChipState>().single()
                // equality
                "Issued amounts of billing states should be equal" using (inputBillingState.issued == outputBillingState.issued)
                "Issuer of billing states should be equal" using (inputBillingState.issuer == outputBillingState.issuer)
                "Linear id of billing states should be equal" using (inputBillingState.linearId == outputBillingState.linearId)
                "Expiry date of billing states should be equal" using (inputBillingState.expiryDate == outputBillingState.expiryDate)
                "Owner of billing states should be equal" using (inputBillingState.owner == outputBillingState.owner)
                "Owner of chip state should be the same as of billing state" using (outputChipState.owner == outputBillingState.owner)
                "Linear id of the billing state should match the chip state" using (outputBillingState.linearId == outputChipState.billingStateLinearId)

                //amounts
                "Spent amount of the output billing state should be positive" using (outputBillingState.spent >= 0L)
                "Amount of the billing chip state should be positive" using (outputChipState.amount > 0L)
                "Spent amount of the output state should be incremented on chip off value" using (outputBillingState.spent == inputBillingState.spent + outputChipState.amount)

                //signers and participants
                "ChipOff transaction should be signed only by the owner" using (command.signers.single() == outputBillingState.owner.owningKey)

                // spent constraint
                if (outputBillingState.issued > 0L) {
                    "Spent amount of the billing state should be less than the issued" using (outputBillingState.spent <= outputBillingState.issued)
                }
                if (outputBillingState.expiryDate != null) {
                    val timeWindow = tx.timeWindow!!
                    "Billing state expiry date should be within the specified time window" using (timeWindow.contains(outputBillingState.expiryDate))
                }
            }
            is Commands.Spend -> requireThat {
                "Spend transaction should not have BillingStates as inputs" using (tx.inputsOfType<BillingState>().isEmpty())
                "Spend transaction should not have BillingStates as outputs" using (tx.outputsOfType<BillingState>().isEmpty())
                "Spend transaction should not have BillingChipState as outputs" using (tx.outputsOfType<BillingChipState>().isEmpty())
                val inputChipState = tx.inputsOfType<BillingChipState>().single()
                "Spend transaction should be signed by BillingChipState owner" using (command.signers.single() == inputChipState.owner.owningKey)
                val billingStateRefInput = tx.referenceInputRefsOfType<BillingState>().single()
                "Spend transaction should contain BillingState as a reference input" using (billingStateRefInput.state.data.linearId == inputChipState.billingStateLinearId)
                if (billingStateRefInput.state.data.expiryDate != null) {
                    val timeWindow = tx.timeWindow!!
                    "Billing state expiry date should be within the specified time window" using (timeWindow.contains(billingStateRefInput.state.data.expiryDate!!))
                }
            }
            is Commands.Retire -> requireThat {
                "Retire transaction should contain no outputs of type BillingState" using (tx.outputsOfType<BillingState>().isEmpty())
                "Retire transaction should contain no outputs of type BillingChipState" using (tx.outputsOfType<BillingChipState>().isEmpty())
                "Retire transaction should contain no inputs of type BillingChipState" using (tx.inputsOfType<BillingChipState>().isEmpty())
                val billingStateInput = tx.inputsOfType<BillingState>().single()
                "The issuer of billing state should be a signer" using (command.signers.single() == billingStateInput.issuer.owningKey)
            }
            else -> throw IllegalArgumentException("Unsupported command ${command.value}")
        }
    }
}

@BelongsToContract(BillingContract::class)
data class BillingState(
        val issuer: Party,
        val owner: Party,
        val issued: Long,
        val spent: Long,
        val expiryDate : Instant? = null,
        override val linearId : UniqueIdentifier = UniqueIdentifier()
) : LinearState {
    override val participants = listOf(owner, issuer)

    fun chipOff(amount : Long) : Pair<BillingState, BillingChipState>
            = Pair(copy(spent = spent + amount), BillingChipState(issuer, amount, linearId))
}

@BelongsToContract(BillingContract::class)
data class BillingChipState (
        val owner: Party,
        val amount: Long,
        val billingStateLinearId : UniqueIdentifier
) : ContractState {
    override val participants = listOf(owner)
}
