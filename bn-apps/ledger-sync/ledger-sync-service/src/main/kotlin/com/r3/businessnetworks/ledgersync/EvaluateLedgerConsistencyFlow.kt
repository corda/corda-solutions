package com.r3.businessnetworks.ledgersync

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.ProgressTracker
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

    override val progressTracker: ProgressTracker = ProgressTracker()

    @Suspendable
    override fun call(): Map<Party, Boolean> = (members - ourIdentity)
            .map { they ->
                val knownTransactionsHash = serviceHub.withParticipants(ourIdentity, they).hash()
                val findings = initiateFlow(they).sendAndReceive<Boolean>(knownTransactionsHash as OpaqueBytes).unwrap { it }
                they to findings
            }.toMap()
}

@Suppress("unused")
@InitiatedBy(EvaluateLedgerConsistencyFlow::class)
class RespondEvaluateLedgersConsistencyFlow(
        private val otherSideSession: FlowSession
) : FlowLogic<Unit>() {
    /**
     * Depending on the use case, users of this flow might introduce additional validation logic. This could mean
     * making `RespondLedgerSyncFlow` implement `BusinessNetworkAwareInitiatedFlow`, i.e.:
     *
     *      class RespondLedgerSyncFlow(
     *          private val otherSideSession: FlowSession
     *      ) : BusinessNetworkAwareInitiatedFlow<Unit>(otherSideSession)
     *
     * Subsequently `onCounterpartyMembershipVerified` can be used.
     */

    @Suspendable
    override fun call() {
        val theirHash = otherSideSession.receive<OpaqueBytes>().unwrap { it }
        val ourHash = serviceHub.withParticipants(ourIdentity, otherSideSession.counterparty).hash()
        otherSideSession.send(theirHash == ourHash)
    }
}
