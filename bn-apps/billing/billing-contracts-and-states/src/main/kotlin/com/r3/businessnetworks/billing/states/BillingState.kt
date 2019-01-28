package com.r3.businessnetworks.billing.states

import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import java.lang.IllegalArgumentException
import java.time.Instant


class BillingContract : Contract {
    interface Commands : CommandData {
        class Issue : Commands, TypeOnlyCommandData()
        class ChipOff : Commands, TypeOnlyCommandData()
        class Spend : Commands, TypeOnlyCommandData()
    }

    override fun verify(tx : LedgerTransaction) {
        val command = tx.commands.requireSingleCommand(Commands::class.java)
        when (command.value) {
            is Commands.Issue -> {

            }
            is Commands.ChipOff -> {

            }
            is Commands.Spend -> {

            }
            else -> throw IllegalArgumentException("Unsupported command ${command.value}")
        }
    }
}

@BelongsToContract(BillingContract::class)
data class BillingState(
        val amount: Long,
        val party: Party,
        val issuer: Party,
        val expiryDate: Instant? = null
) : ContractState {
    override val participants = listOf(party, issuer)
}

@BelongsToContract(BillingContract::class)
data class BillingChip (
        val amount: Long,
        val party: Party,
        val issuer: Party
) : ContractState {
    override val participants = listOf(party)
}
