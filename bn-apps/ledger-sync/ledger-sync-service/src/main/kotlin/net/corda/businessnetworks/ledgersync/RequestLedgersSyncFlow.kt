package net.corda.businessnetworks.ledgersync

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.states.Membership
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.unwrap

typealias MissingIds = Map<Party, Set<SecureHash>>

@InitiatingFlow
@StartableByRPC
class RequestLedgersSyncFlow(
        private val members: Map<Party, StateAndRef<Membership.State>>
) : FlowLogic<MissingIds>() {

    @Suspendable
    override fun call(): MissingIds {
        // only consider active members that are not us
        val relevantMembers = members.filter { (_, stateAndRef) ->
            stateAndRef.state.data.isActive()
        }.filterNot { (party, _) ->
            party == ourIdentity
        }

        return relevantMembers.keys.toList().map { they ->
            val knownTransactionsIds = serviceHub.vaultService.withParticipants(ourIdentity, they)
            // TODO moritzplatt 06/08/2018 -- what level of validation is needed?
            they to initiateFlow(they).sendAndReceive<Set<SecureHash>>(knownTransactionsIds).unwrap { it }
        }.toMap()
    }
}

@InitiatedBy(RequestLedgersSyncFlow::class)
class RequestLedgerSyncFlow(
        val otherSideSession: FlowSession
) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        // TODO moritzplatt 06/08/2018 -- validate requester is part of our business network

        val transactionsTheSenderIsAwareOf = otherSideSession
                .receive<Set<SecureHash>>()
                .unwrap { it }

        // TODO moritzplatt 06/08/2018 -- is `otherSideSession.counterparty` actually what we're looking for?
        val transactionsWeAreAwareOf = serviceHub.vaultService.withParticipants(ourIdentity, otherSideSession.counterparty)

        // TODO moritzplatt 06/08/2018 -- what to do if the sender holds a superset of transactions (protocol violation)
        otherSideSession.send(transactionsWeAreAwareOf - transactionsTheSenderIsAwareOf)
    }
}

private fun VaultService.withParticipants(vararg parties: Party): Set<SecureHash> {
    val result = queryBy<ContractState>(QueryCriteria.VaultQueryCriteria())
    // TODO moritzplatt 06/08/2018 -- use database query to filter proper

    // TODO moritzplatt 06/08/2018 -- what to do for paging? 1 giant page? iterate and merge? or send blocks of tx in distinct flows?
    return result.states.filter {
        // TODO moritzplatt 06/08/2018 -- filter in query
        it.state.data.participants.containsAll(parties.toSet())
    }.map {
        // TODO moritzplatt 06/08/2018 -- is this the "transaction id"?
        it.ref.txhash
    }.toSet()
}
