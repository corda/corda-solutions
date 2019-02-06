package com.r3.businessnetworks.billing.states

import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.Command
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import java.time.Instant

class BillingContract : Contract {
    companion object {
        const val CONTRACT_NAME = "com.r3.businessnetworks.billing.states.BillingContract"
    }

    interface Commands : CommandData {
        // billing state-related commands
        class Issue : Commands, TypeOnlyCommandData()
        class Return : Commands, TypeOnlyCommandData()
        class Revoke : Commands, TypeOnlyCommandData()
        class Close : Commands, TypeOnlyCommandData()
        // chip related commands
        class ChipOff : Commands, TypeOnlyCommandData() //  split
        class AttachBack : Commands, TypeOnlyCommandData() // combine
        data class UseChip(val owner : Party) : Commands // use
    }

    override fun verify(tx : LedgerTransaction) {
        val commands = tx.commandsOfType<Commands>()

        if (commands.isEmpty()) {
            throw IllegalArgumentException("Transaction must contain at least one of the BillingContract commands")
        }

        // UseChip command requires different handling as there could be multiple instances of those inside one transaction
        // Note: at this point [commands] list contains only commands that are related to the [BillingContract]
        // as everything else has been filtered ut in the very beginning
        if (commands.first().value is Commands.UseChip) {
            verifyUseChipTransaction(tx, commands)
        } else {
            val command = commands.single()
            when (command.value) {
                is Commands.Issue -> verifyIssueTransaction(tx, command)
                is Commands.Return -> verifyReturnTransaction(tx, command)
                is Commands.Revoke -> verifyRevokeTransaction(tx, command)
                is Commands.Close -> verifyCloseTransaction(tx, command)
                is Commands.ChipOff -> verifyChipOffTransaction(tx, command)
                is Commands.AttachBack -> verifyAttachBackTransaction(tx, command)
                else -> throw IllegalArgumentException("Unsupported command ${command.value}")
            }
        }
    }

    private fun verifyIssueTransaction(tx: LedgerTransaction, command : Command<Commands>) = requireThat {
        "There should be no inputs" using (tx.inputs.isEmpty())
        "There should be a single output of BillingState type" using (tx.outputs.size == 1 && tx.outputStates.single() is BillingState)

        val billingState = tx.outputsOfType<BillingState>().single()

        "Both the owner and the issuer should be signers" using (command.signers.toSet() == setOf(billingState.issuer.owningKey, billingState.owner.owningKey))
        "Issued amount should not be negative" using (billingState.issued >= 0L)
        "Spent amount should be zero" using (billingState.spent == 0L)
        "BillingState status should be ACTIVE" using (billingState.status == BillingStateStatus.ACTIVE)
    }

    private fun verifyChipOffTransaction(tx : LedgerTransaction, command : Command<Commands>) = requireThat {
        "There should be a single input of BillingState type" using (tx.inputs.size == 1 && tx.inputs.single().state.data is BillingState)
        "There should be one output of BillingState type" using (tx.outputsOfType<BillingState>().size == 1)
        "There should be at least one output of BillingChipState type" using (tx.outputsOfType<BillingChipState>().isNotEmpty())
        "There should be no other outputs but BillingState and BillingChipState types" using
                (tx.outputStates.count { it !is BillingState && it !is BillingChipState } == 0)

        val inputBillingState = tx.inputsOfType<BillingState>().single()
        val outputBillingState = tx.outputsOfType<BillingState>().single()
        val outputChipStates = tx.outputsOfType<BillingChipState>()

        "Input BillingState status should be ACTIVE" using (inputBillingState.status == BillingStateStatus.ACTIVE)
        "Input and output BillingStates should be equal except the `spent` field" using (inputBillingState == outputBillingState.copy(spent = inputBillingState.spent))
        "Only owner should be a signer" using (command.signers.size ==1 && command.signers.single() == outputBillingState.owner.owningKey)

        var totalAmount = 0L
        outputChipStates.forEach {outputChipState ->
            "Owner of BillingChips should match the BillingStates" using (outputChipState.owner == outputBillingState.owner)
            "Issuer of BillingChips should match the BillingStates" using (outputChipState.issuer == outputBillingState.issuer)
            "Linear id BillingChips should match the BillingStates" using (outputBillingState.linearId == outputChipState.billingStateLinearId)
            "Amount of BillingChips should be positive" using (outputChipState.amount > 0L)
            val previousAmount = totalAmount
            totalAmount += outputChipState.amount
            "Total chip off value should not exceed Long.MAX_VALUE" using (totalAmount > previousAmount)
        }

        "Spent amount of the output BillingState should be incremented on the total of the chip off value" using (outputBillingState.spent == inputBillingState.spent + totalAmount)

        // spent constraint
        if (outputBillingState.issued > 0L) {
            "Spent amount of the output BillingState should be less or equal to the issued" using (outputBillingState.spent <= outputBillingState.issued)
        }
        if (outputBillingState.expiryDate != null) {
            verifyTimeWindow(outputBillingState.expiryDate, tx)
        }
    }

    private fun verifyUseChipTransaction(tx : LedgerTransaction, commands : List<Command<BillingContract.Commands>>) = requireThat {
        "UseChip transaction can contain only UseChip commands" using (commands.find { it.value !is Commands.UseChip } == null)
        "UseChip transaction should not have BillingStates in inputs" using (tx.inputsOfType<BillingState>().isEmpty())
        "UseChip transaction should not have BillingStates in outputs" using (tx.outputsOfType<BillingState>().isEmpty())
        "UseChip transaction should not have BillingChipState in outputs" using (tx.outputsOfType<BillingChipState>().isEmpty())

        val commandsByOwner = commands.map {
            val useChipCommand = it.value as Commands.UseChip
            "UseChip command owner should be the only signer" using (it.signers.size == 1 && useChipCommand.owner.owningKey == it.signers.single())
            useChipCommand.owner to Command(useChipCommand, it.signers)
        }.toMap()

        val billingStateByLinearId = tx.referenceInputsOfType<BillingState>().map { it.linearId to it }.toMap()

        tx.inputsOfType<BillingChipState>().forEach {
            "There should be a UseChip command for each BillingChip owner" using (commandsByOwner[it.owner] != null)
            val billingState = billingStateByLinearId[it.billingStateLinearId]
            "There should be a reference BillingState for each BillingChip" using (billingState != null
                    && billingState.isChipValid(it))
            "Reference BillingState status should be ACTIVE" using (billingState!!.status == BillingStateStatus.ACTIVE)
            if (billingState.expiryDate != null) {
                verifyTimeWindow(billingState.expiryDate, tx)
            }
        }
    }

    private fun verifyAttachBackTransaction(tx : LedgerTransaction, command : Command<Commands>) = requireThat {
        "Should have one input of BillingState type" using (tx.inputsOfType<BillingState>().size == 1)
        "Should have at least one input of BillingChipState type" using (tx.inputsOfType<BillingChipState>().isNotEmpty())
        "Should have a single output of BillingState type" using (tx.outputs.size == 1 && tx.outputStates.single() is BillingState)
        "Should have inputs only of BillingState and BillingChipState types" using (tx.inputStates.count { it !is BillingState && it !is BillingChipState } == 0)

        val inputBillingState = tx.inputsOfType<BillingState>().single()
        val outputBillingState = tx.outputStates.single() as BillingState
        val chips = tx.inputsOfType<BillingChipState>()

        "Input and output BillingStates should be equal except the `spent` field" using (inputBillingState == outputBillingState.copy(spent = inputBillingState.spent))
        "Spent amount of the output BillingState should be not negative" using (outputBillingState.spent >= 0)
        "AttachBack transaction should be signed only by the owner" using (command.signers.size == 1 && command.signers.single() == outputBillingState.owner.owningKey)
        "BillingState status should be ACTIVE" using (inputBillingState.status == BillingStateStatus.ACTIVE)

        var totalAmount = 0L
        chips.forEach {
            "BillingChipStates should match BillingStates" using (outputBillingState.isChipValid(it))
            "BillingChipState amount should be positive" using (it.amount > 0L)
            val previousAmount = totalAmount
            totalAmount += it.amount
            "Total AttachBack value should not exceed Long.MAX_VALUE" using (totalAmount > previousAmount)
        }

        "Spent amount of the output BillingState should be decremented on the total of the chip off value" using (inputBillingState.spent - totalAmount == outputBillingState.spent)

        if (outputBillingState.expiryDate != null) {
            verifyTimeWindow(outputBillingState.expiryDate, tx)
        }
    }

    private fun verifyReturnTransaction(tx : LedgerTransaction, command : Command<Commands>) = verifyReturnRevokeClose(
            tx, command, listOf(BillingStateStatus.ACTIVE), BillingStateStatus.RETURNED, true, true
    )

    private fun verifyRevokeTransaction(tx : LedgerTransaction, command : Command<Commands>) = verifyReturnRevokeClose(
            tx, command, listOf(BillingStateStatus.ACTIVE), BillingStateStatus.REVOKED, true, false
    )

    private fun verifyCloseTransaction(tx : LedgerTransaction, command : Command<Commands>) = verifyReturnRevokeClose(
            tx, command, listOf(BillingStateStatus.RETURNED, BillingStateStatus.REVOKED), BillingStateStatus.CLOSED, true, false
    )

    private fun verifyReturnRevokeClose(tx : LedgerTransaction,
                                        command : Command<Commands>,
                                        inputStateStatuses : List<BillingStateStatus>,
                                        outputStateStatus : BillingStateStatus,
                                        issuerIsSigner : Boolean,
                                        ownerIsSigner : Boolean) = requireThat {
        "There should be a single output of BillingState type" using (tx.outputs.size == 1 && tx.outputStates.single() is BillingState)
        "There should be a single input of BillingState type" using (tx.inputs.size == 1 && tx.inputStates.single() is BillingState)
        val inputBillingState = tx.inputsOfType<BillingState>().single()
        val outputBillingState = tx.outputsOfType<BillingState>().single()
        val signers = mutableListOf<Party>()
        if (issuerIsSigner) signers.add(inputBillingState.issuer)
        if (ownerIsSigner) signers.add(inputBillingState.owner)
        "$signers should be transaction signers" using (command.signers.toSet() == signers.map { it.owningKey }.toSet())
        "Input and output BillingsState should be equal but the `status` field" using (inputBillingState == outputBillingState.copy(status = inputBillingState.status))
        "Input BillingsState status should be one of $inputStateStatuses" using (inputBillingState.status in inputStateStatuses)
        "Output BillingsState status should be $outputStateStatus" using (outputBillingState.status == outputStateStatus)

    }

    private fun verifyTimeWindow(expiryDate : Instant, tx: LedgerTransaction) = requireThat {
        // expiry date should be less than the upper boundary of the transaction time window
        "Output BillingState expiry date should be within the specified time window" using (tx.timeWindow != null
                && tx.timeWindow!!.untilTime != null
                && tx.timeWindow!!.untilTime!! <= expiryDate)

    }
}

@BelongsToContract(BillingContract::class)
data class BillingState(
        val issuer: Party,
        val owner: Party,
        val issued: Long,
        val spent: Long,
        val status : BillingStateStatus = BillingStateStatus.ACTIVE,
        val expiryDate : Instant? = null,
        override val linearId : UniqueIdentifier = UniqueIdentifier()
) : LinearState, QueryableState {
    override fun generateMappedObject(schema : MappedSchema) : PersistentState {
        return when (schema) {
            is BillingStateSchemaV1 -> BillingStateSchemaV1.PersistentBillingState(issuer, owner, status)
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }
    override fun supportedSchemas() = listOf(BillingStateSchemaV1)

    override val participants = listOf(owner, issuer)

    fun chipOff(amount : Long) : Pair<BillingState, BillingChipState>
            = Pair(copy(spent = spent + amount), BillingChipState(issuer, owner, amount, linearId))

    fun isChipValid(chip : BillingChipState) =
                    owner == chip.owner
                    && issuer == chip.issuer
                    && linearId == chip.billingStateLinearId
}

@CordaSerializable
enum class BillingStateStatus {
    ACTIVE, RETURNED, REVOKED, CLOSED
}

@BelongsToContract(BillingContract::class)
data class BillingChipState (
        val issuer: Party,
        val owner: Party,
        val amount: Long,
        val billingStateLinearId : UniqueIdentifier
) : ContractState, QueryableState {
    override val participants = listOf(owner)

    override fun generateMappedObject(schema : MappedSchema) : PersistentState {
        return when (schema) {
            is BillingChipStateSchemaV1 -> BillingChipStateSchemaV1.PersistentBillingChipState(issuer, amount, billingStateLinearId.toString())
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }
    override fun supportedSchemas() = listOf(BillingChipStateSchemaV1)

}
