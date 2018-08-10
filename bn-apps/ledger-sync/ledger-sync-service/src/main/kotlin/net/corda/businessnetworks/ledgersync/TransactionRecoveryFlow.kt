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

@InitiatingFlow
class TransactionRecoveryFlow(
        private val report: Map<Party, LedgerSyncFindings>
) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        report.forEach { counterParty, findings ->
            val flow = initiateFlow(counterParty)

            findings.recoverable().also {
                flow.send(it)
            }.forEach {
                subFlow(ReceiveTransactionFlow(flow))
            }
        }
    }

    private fun LedgerSyncFindings.recoverable() = missingAtRequestee - missingAtRequester
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
