package com.r3.businessnetworks.ledgersync

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

/**
 * If inconsistencies are flagged, the counterparty can be queried for a more detailed report using this flow. Based
 * the result, both the parties can take further action (such as notifying the BNO) or recover the transactions found.
 */
@InitiatingFlow
@StartableByRPC
class RequestLedgersSyncFlow(
        private val members: List<Party>
) : FlowLogic<Map<Party, LedgerSyncFindings>>() {

    override val progressTracker: ProgressTracker = ProgressTracker()

    @Suspendable
    override fun call(): Map<Party, LedgerSyncFindings> = (members - ourIdentity)
            .map { they ->
                val knownTransactionsIds = serviceHub.withParticipants(ourIdentity, they)
                val ourVr = serviceHub.getRecycledTx()
                val findings = initiateFlow(they).sendAndReceive<LedgerSyncFindings>(knownTransactionsIds).unwrap { it }
                val newFindings = findings.copy(missingAtRequestee = findings.missingAtRequestee - ourVr, missingAtRequester = findings.missingAtRequester - ourVr)
                // an individual implementation might treat findings differently, i.e. report them to the BNO
                they to newFindings
            }.toMap()
}

@Suppress("unused")
@InitiatedBy(RequestLedgersSyncFlow::class)
class RespondLedgerSyncFlow(
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
        val theirs = otherSideSession.receive<List<SecureHash>>().unwrap { it }
        val ours = serviceHub.withParticipants(ourIdentity, otherSideSession.counterparty)
        val ourVr = serviceHub.getRecycledTx()
        otherSideSession.send(LedgerSyncFindings(
                ours - theirs - ourVr,
                theirs - ours - ourVr
        ))
    }
}

/**
 * A class that encapsulates the findings of a [RequestLedgersSyncFlow], indicating which transactions the original
 * party and the counterparty respectively are missing.
 *
 * Here, the "requester" is the party making the original sync flow request and the "requestee" is the counterparty.
 */
@CordaSerializable
data class LedgerSyncFindings(
        val missingAtRequester: List<SecureHash>,
        val missingAtRequestee: List<SecureHash>
)
