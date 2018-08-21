package net.corda.businessnetworks.ledgersync

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.ReceiveTransactionFlow
import net.corda.core.flows.SendTransactionFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord.ALL_VISIBLE
import net.corda.core.utilities.unwrap

/**
 * This flow allows for the recovery of transactions with IDs found previously.
 */
@InitiatingFlow
@StartableByRPC
class TransactionRecoveryFlow(
        private val report: Map<Party, LedgerSyncFindings>
) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        // pre filter map to remove multilateral transactions.
        report.toList().map { (counterParty, findings) ->
            val session = initiateFlow(counterParty)
            findings.recoverable().filter {
                // only attempt to recover transactions that are not present.
                // this is intended to exclude multilateral transactions that have already been recovered
                serviceHub.validatedTransactions.getTransaction(it) == null
            }.also {
                session.send(it)
            }.map {
                subFlow(ReceiveTransactionFlow(session, statesToRecord = ALL_VISIBLE))
            }
        }
    }

    private fun LedgerSyncFindings.recoverable() = missingAtRequester - missingAtRequestee
}

@Suppress("unused")
@InitiatedBy(TransactionRecoveryFlow::class)
class RespondTransactionRecoveryFlow(
        private val otherSideSession: FlowSession
) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        otherSideSession
                .receive<List<SecureHash>>()
                .unwrap { it }
                .mapNotNull { finding ->
                    serviceHub.validatedTransactions.getTransaction(finding)
                }.forEach {
                    subFlow(SendTransactionFlow(otherSideSession, it))
                }
    }
}
