package net.corda.businessnetworks.ledgersync.states

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction

class LedgerSync : Contract {
    companion object {
        val CONTRACT_NAME = "net.corda.businessnetworks.ledgersync.states.Membership"
    }

    open class Commands : CommandData, TypeOnlyCommandData() {
        class Request : Commands()
    }

    override fun verify(tx: LedgerTransaction) {
        TODO("do it!")
    }

    data class State(
            val requester: Party,
            val requestee: Party,
            override val linearId: UniqueIdentifier = UniqueIdentifier()
    ) : LinearState {
        override val participants: List<AbstractParty> = listOf(requester, requestee)
    }
}