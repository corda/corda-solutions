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
        class ChipOff : Commands, TypeOnlyCommandData() //  split
        class AttachBack : Commands, TypeOnlyCommandData() // combine
        data class UseChip(val owner : Party) : Commands // use
        class Retire : Commands, TypeOnlyCommandData()
    }



    override fun verify(tx : LedgerTransaction) {
        val commands = tx.commandsOfType<Commands>()

        if (commands.isEmpty()) {
            throw IllegalArgumentException("Transaction must contain at least one of the BillingContract commands")
        }

        // UseChip command requires different handling as there could be multiple instances of those inside one transaction
        if (commands.first().value is Commands.UseChip) {
            verifyUseChipTransaction(tx, commands)
        } else {
            val command = commands.single()
            when (command.value) {
                is Commands.Issue -> verifyIssueTransaction(tx, command)
                is Commands.ChipOff -> verifyChipOffTransaction(tx, command)
                is Commands.AttachBack -> verifyAttachBackTransaction(tx, command)
                is Commands.Retire -> verifyRetireTransaction(tx, command)
                else -> throw IllegalArgumentException("Unsupported command ${command.value}")
            }
        }
    }

    private fun verifyIssueTransaction(tx: LedgerTransaction, command : Command<Commands>) = requireThat {
        "There should be no inputs of BillingState and BillingChipState types" using (tx.inputsOfType<BillingState>().isEmpty() && tx.inputsOfType<BillingChipState>().isEmpty())
        "There should be no outputs of BillingChipState type" using (tx.outputsOfType<BillingChipState>().isEmpty())
        "There should be one output of BillingState type" using (tx.outputsOfType<BillingState>().size == 1)

        val billingState = tx.outputsOfType<BillingState>().single()

        "Both the owner and the issuer should be signers" using (command.signers.toSet() == setOf(billingState.issuer.owningKey, billingState.owner.owningKey))
        "Issued amount should not be negative" using (billingState.issued >= 0L)
        "Spent amount should be zero" using (billingState.spent == 0L)
    }


    private fun verifyChipOffTransaction(tx : LedgerTransaction, command : Command<Commands>) = requireThat {
        "There should be no inputs of BillingChipState type" using (tx.inputsOfType<BillingChipState>().isEmpty())
        "There should be one input of BillingState type" using (tx.inputsOfType<BillingState>().size == 1)
        "There should be one output of BillingState type" using (tx.outputsOfType<BillingState>().size == 1)
        "There should be at least one output of BillingChipState type" using (tx.outputsOfType<BillingChipState>().isNotEmpty())

        val inputBillingState = tx.inputsOfType<BillingState>().single()
        val outputBillingState = tx.outputsOfType<BillingState>().single()
        val outputChipStates = tx.outputsOfType<BillingChipState>()

        "Input and output BillingStates should be equal except the `spent` field" using (inputBillingState == outputBillingState.copy(spent = inputBillingState.spent))
        "Only owner should be a signer" using (command.signers.single() == outputBillingState.owner.owningKey)

        var totalAmount = 0L
        outputChipStates.forEach {outputChipState ->
            "Owner of ChipStates should match the BillingStates" using (outputChipState.owner == outputBillingState.owner)
            "Linear id ChipStates should match the BillingStates" using (outputBillingState.linearId == outputChipState.billingStateLinearId)
            "Amount of ChipStates should be positive" using (outputChipState.amount > 0L)
            val previousAmount = totalAmount
            totalAmount += outputChipState.amount
            "Total chip off value should not exceed Long.MAX_VALUE" using (totalAmount > previousAmount)
        }

        "Spent amount of the output BillingState should be incremented on the total of the chip off value" using (outputBillingState.spent == inputBillingState.spent + totalAmount)
        "Total chip off value should not overflow spent amount" using (outputBillingState.spent > inputBillingState.spent)

        // spent constraint
        if (outputBillingState.issued > 0L) {
            "Spent amount of the output BillingState should be less or equal to the issued" using (outputBillingState.spent <= outputBillingState.issued)
        }
        if (outputBillingState.expiryDate != null) {
            val timeWindow = tx.timeWindow!!
            "Output BillingState expiry date should be within the specified time window" using (timeWindow.contains(outputBillingState.expiryDate))
        }
    }





    private fun verifyUseChipTransaction(tx : LedgerTransaction, commands : List<Command<BillingContract.Commands>>) = requireThat {
        "UseChip transaction can contain only UseChip commands" using (commands.find { it.value !is Commands.UseChip } == null)

        val spendCommands = commands.map { Command(it.value as Commands.UseChip, it.signers) }

        "UseChip transaction should not have BillingStates as inputs" using (tx.inputsOfType<BillingState>().isEmpty())
        "UseChip transaction should not have BillingStates as outputs" using (tx.outputsOfType<BillingState>().isEmpty())
        "UseChip transaction should not have BillingChipState as outputs" using (tx.outputsOfType<BillingChipState>().isEmpty())

        val chipStates = tx.inputsOfType<BillingChipState>().sortedBy { it.owner.name.toString() }
        val billingRefStates = tx.referenceInputsOfType<BillingState>().sortedBy { it.owner.name.toString() }

        "There should be exactly one BillingChipState and reference BillingState for each UseChip command" using (chipStates.size == billingRefStates.size && billingRefStates.size == commands.size)
        "There should be exactly one UseChip command for each owner" using (spendCommands.map { it.value.owner }.toSet().size == spendCommands.size)

        for (i in 1..spendCommands.size) {
            val billingState = billingRefStates[i]
            val chipState = chipStates[i]
            val spendCommand = spendCommands[i]

            "There should be exactly one BillingChipState and reference BillingState for each UseChip command" using (spendCommand.value.owner == billingState.owner
                    && spendCommand.value.owner == chipState.owner)

            "UseChip commands should be signed by the owner" using (spendCommand.signers.single() == spendCommand.value.owner.owningKey)
            "BillingState and BillingCHipState linear ids should match for the same owner" using (billingState.linearId == chipState.billingStateLinearId)
            if (billingState.expiryDate != null) {
                val timeWindow = tx.timeWindow!!
                "Billing state expiry date should be within the specified time window" using (timeWindow.contains(billingState.expiryDate))
            }
        }
    }




    private fun verifyRetireTransaction(tx : LedgerTransaction, command : Command<Commands>) = requireThat {
        "Retire transaction should contain no outputs of type BillingState" using (tx.outputsOfType<BillingState>().isEmpty())
        "Retire transaction should contain no outputs of type BillingChipState" using (tx.outputsOfType<BillingChipState>().isEmpty())
        "Retire transaction should contain no inputs of type BillingChipState" using (tx.inputsOfType<BillingChipState>().isEmpty())
        val billingStateInput = tx.inputsOfType<BillingState>().single()
        "The issuer of billing state should be a signer" using (command.signers.single() == billingStateInput.issuer.owningKey)
    }

    private fun verifyAttachBackTransaction(tx : LedgerTransaction, command : Command<Commands>) = requireThat {
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
