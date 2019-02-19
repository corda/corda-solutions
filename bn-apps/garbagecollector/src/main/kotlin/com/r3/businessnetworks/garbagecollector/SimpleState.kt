package com.r3.businessnetworks.garbagecollector

import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction

class SimpleContract : Contract {
    companion object {
        const val CONTRACT_ID = "com.r3.businessnetworks.garbagecollector.SimpleContract"
    }

    class SimpleCommand : CommandData, TypeOnlyCommandData()

    override fun verify(tx : LedgerTransaction) {
    }
}

@BelongsToContract(SimpleContract::class)
data class SimpleState(val owner : Party,
                       val tag : String) : ContractState {
    override val participants = listOf(owner)
}