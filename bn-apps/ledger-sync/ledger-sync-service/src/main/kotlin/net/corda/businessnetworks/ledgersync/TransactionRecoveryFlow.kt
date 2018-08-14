package net.corda.businessnetworks.ledgersync

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.ReceiveTransactionFlow
import net.corda.core.flows.SendTransactionFlow
import net.corda.core.identity.Party
import net.corda.core.utilities.unwrap

/**
 * This flow allows for the recovery of transactions with IDs found previously.
 */
@InitiatingFlow
class TransactionRecoveryFlow(
        private val report: Map<Party, LedgerSyncFindings>
) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        report.toList().map { (counterParty, findings) ->
            val session = initiateFlow(counterParty)
            findings.recoverable().also {
                session.send(it)
            }.map {
                subFlow(ReceiveTransactionFlow(session))
            }
        }.flatten().onEach {
            it.verify(serviceHub)
        }.also {
            serviceHub.recordTransactions(it)
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
