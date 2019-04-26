package com.r3.businessnetworks.memberships.demo.contracts

import net.corda.core.contracts.*
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction

class AssetContractCounterPartyChecks : Contract {
    companion object {
        const val CONTRACT_NAME = "com.r3.businessnetworks.memberships.demo.contracts.AssetContractCounterPartyChecks"
    }

    sealed class Commands : CommandData, TypeOnlyCommandData() {
        class Issue : Commands()
        class Transfer : Commands()
    }

    override fun verify(tx: LedgerTransaction) {
        // there should be only one asset command
        val assetCommand = tx.commandsOfType<Commands>().single()

        when (assetCommand.value) {
            is Commands.Issue -> requireThat {
                "There should be no inputs of type AssetStateCounterPartyChecks" using (tx.inputsOfType<AssetStateCounterPartyChecks>().isEmpty())
                "There should be a single output of type AssetStateCounterPartyChecks" using (tx.outputsOfType<AssetStateCounterPartyChecks>().size == 1)
                val outputState = tx.outputsOfType<AssetStateCounterPartyChecks>().single()
                "Owner of the state should be a signer" using (assetCommand.signers.single() == outputState.owner.owningKey)
            }
            is Commands.Transfer -> requireThat {
                "There should be one input of type SampleState" using (tx.inputsOfType<AssetStateCounterPartyChecks>().size == 1)
                "There should be a single output of type SampleState" using (tx.outputsOfType<AssetStateCounterPartyChecks>().size == 1)
                val inputState = tx.inputsOfType<AssetStateCounterPartyChecks>().single()
                val outputState = tx.outputsOfType<AssetStateCounterPartyChecks>().single()
                "Input and output states should have different owners" using (inputState.owner != outputState.owner)
                "Both of the states should be signers" using (assetCommand.signers.toSet() == setOf(inputState.owner.owningKey, outputState.owner.owningKey))
            }
            else -> throw IllegalArgumentException("Unsupported command ${assetCommand.value}")
        }
    }

}

@BelongsToContract(AssetContractCounterPartyChecks::class)
data class AssetStateCounterPartyChecks(val owner: Party) : ContractState {
    override val participants = listOf(owner)
}
