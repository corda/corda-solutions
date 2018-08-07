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
        // TODO moritzplatt 07/08/2018 -- List<Party>
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

        // TODO moritzplatt 07/08/2018 -- notary?

        return relevantMembers.keys.toList().map { they ->
            val knownTransactionsIds = serviceHub.vaultService.withParticipants(ourIdentity, they)

            // TODO moritzplatt 06/08/2018 -- what level of validation is needed?
            val flow = initiateFlow(they)
            val unwrap = flow.sendAndReceive<Set<SecureHash>>(knownTransactionsIds).unwrap { it }
            they to unwrap
        }.toMap()
    }
}

@InitiatedBy(RequestLedgersSyncFlow::class)
class RespondLedgerSyncFlow(
        val otherSideSession: FlowSession
) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        // TODO moritzplatt 06/08/2018 -- make public comment about necessity to determine BN membership

        val transactionsTheSenderIsAwareOf = otherSideSession
                .receive<Set<SecureHash>>()
                .unwrap { it }

        // TODO moritzplatt 06/08/2018 -- is `otherSideSession.counterparty` actually what we're looking for?
        val transactionsWeAreAwareOf = serviceHub.vaultService.withParticipants(ourIdentity, otherSideSession.counterparty)

        // TODO moritzplatt 07/08/2018 -- instead of returning that, use new data class
        val payload = transactionsWeAreAwareOf - transactionsTheSenderIsAwareOf
        otherSideSession.send(payload)
    }
}

private fun VaultService.withParticipants(vararg parties: Party): Set<SecureHash> {
    // TODO moritzplatt 07/08/2018 -- discuss best query strategy with #development
    val result = queryBy<ContractState>(QueryCriteria.VaultQueryCriteria())
    // TODO moritzplatt 06/08/2018 -- use database query to filter proper

    // TODO moritzplatt 06/08/2018 -- what to do for paging? 1 giant page? iterate and merge? or send blocks of tx in distinct flows?

    // consider largest possible message size

    return result.states.filter {
        // TODO moritzplatt 06/08/2018 -- filter in query
        it.state.data.participants.containsAll(parties.toSet())
    }.map {
        // TODO moritzplatt 06/08/2018 -- is this the "transaction id"?
        it.ref.txhash
    }.toSet()
}

data class LedgerSyncResult(
        val whatWeAreMissing: Set<SecureHash>,
        val whatTheyAreMissing: Set<SecureHash>
)