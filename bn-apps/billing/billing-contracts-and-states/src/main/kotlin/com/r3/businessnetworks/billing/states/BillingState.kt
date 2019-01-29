package com.r3.businessnetworks.billing.states

import net.corda.core.contracts.*
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import java.lang.IllegalArgumentException
import java.time.Instant

class BillingContract : Contract {
    companion object {
        const val CONTRACT_NAME = "com.r3.businessnetworks.billing.states.BillingContract"
    }

    interface Commands : CommandData {
        class Issue : Commands, TypeOnlyCommandData()
        class ChipOff : Commands, TypeOnlyCommandData()
        class AttachBack : Commands, TypeOnlyCommandData()
        data class SpendChip(val owner : Party) : Commands
        class Retire : Commands, TypeOnlyCommandData()
    }

    override fun verify(tx : LedgerTransaction) {
        val commands = tx.commandsOfType<Commands>()

        if (commands.isEmpty()) {
            throw IllegalArgumentException("Transaction must contain at least one BillingContract command")
        }

        if (commands.first().value is Commands.SpendChip) {
            verifySpendTransaction(tx, commands)
        } else {
            // if command is *not* SpendChip then there could be only one command in the transaction
            val command = commands.single()
            when (command.value) {
                is Commands.Issue -> verifyIssueTransaction(tx, command)
                is Commands.ChipOff -> verifyChipOffTransaction(tx, command)
                is Commands.Retire -> verifyRetireTransaction(tx, command)
                is Commands.AttachBack -> verifyAttachTransaction(tx, command)
                else -> throw IllegalArgumentException("Unsupported command ${command.value}")
            }
        }
    }

    private fun verifySpendTransaction(tx : LedgerTransaction, commands : List<Command<BillingContract.Commands>>) = requireThat {
        "SpendChip transaction can contain only SpendChip commands" using (commands.find { it.value !is Commands.SpendChip } == null)

        val spendCommands = commands.map { Command(it.value as Commands.SpendChip, it.signers) }

        "SpendChip transaction should not have BillingStates as inputs" using (tx.inputsOfType<BillingState>().isEmpty())
        "SpendChip transaction should not have BillingStates as outputs" using (tx.outputsOfType<BillingState>().isEmpty())
        "SpendChip transaction should not have BillingChipState as outputs" using (tx.outputsOfType<BillingChipState>().isEmpty())

        val chipStates = tx.inputsOfType<BillingChipState>().sortedBy { it.owner.name.toString() }
        val billingRefStates = tx.referenceInputsOfType<BillingState>().sortedBy { it.owner.name.toString() }

        "There should be exactly one BillingChipState and reference BillingState for each SpendChip command" using (chipStates.size == billingRefStates.size && billingRefStates.size == commands.size)
        "There should be exactly one SpendChip command for each owner" using (spendCommands.map { it.value.owner }.toSet().size == spendCommands.size)

        for (i in 1..spendCommands.size) {
            val billingState = billingRefStates[i]
            val chipState = chipStates[i]
            val spendCommand = spendCommands[i]

            "There should be exactly one BillingChipState and reference BillingState for each SpendChip command" using (spendCommand.value.owner == billingState.owner
                    && spendCommand.value.owner == chipState.owner)

            "SpendChip commands should be signed by the owner" using (spendCommand.signers.single() == spendCommand.value.owner.owningKey)
            "BillingState and BillingCHipState linear ids should match for the same owner" using (billingState.linearId == chipState.billingStateLinearId)
            if (billingState.expiryDate != null) {
                val timeWindow = tx.timeWindow!!
                "Billing state expiry date should be within the specified time window" using (timeWindow.contains(billingState.expiryDate))
            }
        }
    }

    private fun verifyIssueTransaction(tx: LedgerTransaction, command : Command<Commands>) = requireThat {
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

    private fun verifyChipOffTransaction(tx : LedgerTransaction, command : Command<Commands>) = requireThat {
        "There should be no inputs of type BillingChipState in ChipOff transaction" using (tx.inputsOfType<BillingChipState>().isEmpty())
        val inputBillingState = tx.inputsOfType<BillingState>().single()
        val outputBillingState = tx.outputsOfType<BillingState>().single()
        val outputChipStates = tx.outputsOfType<BillingChipState>()

        "Issued amounts of billing states should be equal" using (inputBillingState.issued == outputBillingState.issued)
        "Issuer of billing states should be equal" using (inputBillingState.issuer == outputBillingState.issuer)
        "Linear id of billing states should be equal" using (inputBillingState.linearId == outputBillingState.linearId)
        "Expiry date of billing states should be equal" using (inputBillingState.expiryDate == outputBillingState.expiryDate)
        "Owner of billing states should be equal" using (inputBillingState.owner == outputBillingState.owner)
        "Spent amount of the output billing state should be positive" using (outputBillingState.spent >= 0L)
        "ChipOff transaction should be signed only by the owner" using (command.signers.single() == outputBillingState.owner.owningKey)


        var totalAmount = 0L
        outputChipStates.forEach {outputChipState ->
            "Owner of chip state should be the same as of billing state" using (outputChipState.owner == outputBillingState.owner)
            "Linear id of the billing state should match the chip state" using (outputBillingState.linearId == outputChipState.billingStateLinearId)
            "Amount of the billing chip state should be positive" using (outputChipState.amount > 0L)
            totalAmount += outputChipState.amount
        }

        "Spent amount of the output state should be incremented on chip off value" using (outputBillingState.spent == inputBillingState.spent + totalAmount)

        // spent constraint
        if (outputBillingState.issued > 0L) {
            "Spent amount of the billing state should be less than the issued" using (outputBillingState.spent <= outputBillingState.issued)
        }
        if (outputBillingState.expiryDate != null) {
            val timeWindow = tx.timeWindow!!
            "Billing state expiry date should be within the specified time window" using (timeWindow.contains(outputBillingState.expiryDate))
        }
    }

    private fun verifyRetireTransaction(tx : LedgerTransaction, command : Command<Commands>) = requireThat {
        "Retire transaction should contain no outputs of type BillingState" using (tx.outputsOfType<BillingState>().isEmpty())
        "Retire transaction should contain no outputs of type BillingChipState" using (tx.outputsOfType<BillingChipState>().isEmpty())
        "Retire transaction should contain no inputs of type BillingChipState" using (tx.inputsOfType<BillingChipState>().isEmpty())
        val billingStateInput = tx.inputsOfType<BillingState>().single()
        "The issuer of billing state should be a signer" using (command.signers.single() == billingStateInput.issuer.owningKey)
    }

    private fun verifyAttachTransaction(tx : LedgerTransaction, command : Command<Commands>) = requireThat {
        val inputBillingState = tx.inputsOfType<BillingState>().single()
        val outputBillingState = tx.outputsOfType<BillingState>().single()
        val chips = tx.inputsOfType<BillingChipState>()

        "AttachBack transaction should not have BillingChipState in outputs" using (tx.outputsOfType<BillingChipState>().isEmpty())

        "Issued amounts of billing states should be equal" using (inputBillingState.issued == outputBillingState.issued)
        "Issuer of billing states should be equal" using (inputBillingState.issuer == outputBillingState.issuer)
        "Linear id of billing states should be equal" using (inputBillingState.linearId == outputBillingState.linearId)
        "Expiry date of billing states should be equal" using (inputBillingState.expiryDate == outputBillingState.expiryDate)
        "Owner of billing states should be equal" using (inputBillingState.owner == outputBillingState.owner)
        "Spent amount of the output billing state should be positive" using (outputBillingState.spent >= 0L)
        "AttachBack transaction should be signed only by the owner" using (command.signers.single() == outputBillingState.owner.owningKey)

        var totalAttach = 0L
        chips.forEach {
            "Owner of BillingChipState should be the same as of BillingStates" using (it.owner == outputBillingState.owner)
            "Linear id of BillingChipState should be the same as of BillingState" using (it.billingStateLinearId == outputBillingState.linearId)
            totalAttach += it.amount
        }

        "Attached amounts should match" using (inputBillingState.spent - totalAttach == outputBillingState.spent)
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
