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
import net.corda.core.utilities.contextLogger


/**
 * This flow is designed only for getting the detailed report between a Party node and the Regulator node.
 * Based on this result, the Party node can recover its lost transaction records by referencing the Regulator node
 * in some further actions (such as notifying the BNO) or recover the transactions found from Regulator node's ledger.
 */
@InitiatingFlow
@StartableByRPC
class RequestRegulatorLedgerSyncFlow(
        private val members: List<Party>
) : FlowLogic<Map<Party, LedgerSyncFindings>>() {

    override val progressTracker: ProgressTracker = ProgressTracker()

    companion object {
        private val logger = contextLogger()
    }

    @Suspendable
    override fun call(): Map<Party, LedgerSyncFindings> = (members - ourIdentity)
            .map { they ->
                //val knownTransactionsIds = serviceHub.vaultService.withParticipants(ourIdentity, they)
                val knownTransactionsIds = serviceHub.vaultService.withParticipants(ourIdentity)
                logger.info("they in RequestRegulatorLedgerSyncFlow is: $they")
                logger.info("ourIdentity in RequestRegulatorLedgerSyncFlow is: $ourIdentity")
                logger.info("knownTransactionsIds is: $knownTransactionsIds")

                val findings = initiateFlow(they).sendAndReceive<LedgerSyncFindings>(knownTransactionsIds).unwrap { it }
                // an individual implementation might treat findings differently, i.e. report them to the BNO
                they to findings
            }.toMap()
}

@Suppress("unused")
@InitiatedBy(RequestRegulatorLedgerSyncFlow::class)
class RespondRegulatorLedgerSyncFlow(
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
        //val ours = serviceHub.vaultService.withParticipants(ourIdentity, otherSideSession.counterparty)
        val ours = serviceHub.vaultService.withParticipants(otherSideSession.counterparty)
        otherSideSession.send(LedgerSyncFindings(
                ours - theirs,
                theirs - ours
        ))
    }
}
