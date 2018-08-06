package net.corda.businessnetworks.ledgersync

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.transactions.LedgerTransaction

class Sync : Contract {
    companion object {
        val CONTRACT_NAME = "net.corda.businessnetworks.ledgersync.LedgerSync"
    }

    open class Commands : CommandData, TypeOnlyCommandData() {
        class Request : Commands()
    }

    override fun verify(tx: LedgerTransaction) {
        TODO("do it!")
    }
}
