package net.corda.businessnetworks.ledgersync

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.unwrap

/**
 * A flow to run a pairwise evaluation to determine if the ledger is in a consistent consistent state with regards to
 * the transactions both parties holds.
 */
@InitiatingFlow
@StartableByRPC
class EvaluateLedgerConsistencyFlow(
        private val members: List<Party>
) : FlowLogic<Map<Party, Boolean>>() {

    @Suspendable
    override fun call(): Map<Party, Boolean> = (members - ourIdentity)
            .map { they ->
                val knownTransactionsHash = serviceHub.vaultService.withParticipants(ourIdentity, they).hash()
                val findings = initiateFlow(they).sendAndReceive<Boolean>(knownTransactionsHash as OpaqueBytes).unwrap { it }
                they to findings
            }.toMap()
}

@Suppress("unused")
@InitiatedBy(EvaluateLedgerConsistencyFlow::class)
class RespondEvaluateLedgersConsistencyFlow(
        private val otherSideSession: FlowSession
) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        /**
         * Depending on the use case, users of this flow might introduce additional validation logic. This could mean
         * ensuring the requester is still a valid member of a network at the time of request, i.e.:
         *
         * subFlow(GetMembershipsFlow()).filter { (party: Party, stateAndRef: StateAndRef<Membership.State>) ->
         *     party == otherSideSession.counterparty && stateAndRef.state.data.status == MembershipStatus.ACTIVE
         * }.toList().firstOrNull() ?: throw FlowException("Ledger consistency check was requested by a party that is not member of the business network")
         */
        val theirHash = otherSideSession.receive<OpaqueBytes>().unwrap { it }
        val ourHash = serviceHub.vaultService.withParticipants(ourIdentity, otherSideSession.counterparty).hash()
        otherSideSession.send(theirHash == ourHash)
    }
}
