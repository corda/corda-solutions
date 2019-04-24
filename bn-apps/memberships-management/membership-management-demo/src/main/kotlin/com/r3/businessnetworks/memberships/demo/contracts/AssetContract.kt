package com.r3.businessnetworks.memberships.demo.contracts

import com.r3.businessnetworks.membership.states.MembershipState
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.contracts.requireThat
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import java.lang.IllegalArgumentException

class AssetContract : Contract {
    companion object {
        const val CONTRACT_NAME = "com.r3.businessnetworks.memberships.demo.contracts.AssetContract"
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
                "There should be no inputs of type AssetState" using (tx.inputsOfType<AssetState>().isEmpty())
                "There should be a single output of type AssetState" using (tx.outputsOfType<AssetState>().size == 1)
                val outputState = tx.outputsOfType<AssetState>().single()
                "Owner of the state should be a signer" using (assetCommand.signers.single() == outputState.owner.owningKey)
                "There should be one reference input state of type MembershipState" using (tx.referenceInputsOfType<MembershipState<Any>>().size == 1)
                // Now we need to verify that the owner of the state has actually a valid membership
                verifyThatParticipantIsMember(outputState.owner, tx)
            }
            is Commands.Transfer -> requireThat {
                "There should be one input of type SampleState" using (tx.inputsOfType<AssetState>().size == 1)
                "There should be a single output of type SampleState" using (tx.outputsOfType<AssetState>().size == 1)
                val inputState = tx.inputsOfType<AssetState>().single()
                val outputState = tx.outputsOfType<AssetState>().single()
                "Input and output states should have different owners" using (inputState.owner != outputState.owner)
                "Both of the states should be signers" using (assetCommand.signers.toSet() == setOf(inputState.owner.owningKey, outputState.owner.owningKey))
                "There should be two reference input state of type MembershipState" using (tx.referenceInputsOfType<MembershipState<Any>>().size == 2)
                // verify that both of the parties have a valid membership
                verifyThatParticipantIsMember(inputState.owner, tx)
                verifyThatParticipantIsMember(outputState.owner, tx)
            }
            else -> throw IllegalArgumentException("Unsupported command ${assetCommand.value}")
        }
    }


    private fun verifyThatParticipantIsMember(party: Party, tx: LedgerTransaction) {
        val membership = tx.referenceInputsOfType<MembershipState<Any>>().single { it.member == party }
        requireThat {
            "Membership test failed: The membership is not active " using (membership.isActive())
        }
    }

}

/**
 * A sample state that parties can issue to themselves and transfer to each other
 */
@BelongsToContract(AssetContract::class)
data class AssetState(val owner: Party) : ContractState {
    override val participants = listOf(owner)
}