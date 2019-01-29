package com.r3.businessnetworks.billing.flows.member

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.billing.flows.bno.AttachUnspentChipsFlow
import com.r3.businessnetworks.billing.states.BillingChipState
import com.r3.businessnetworks.billing.states.BillingContract
import com.r3.businessnetworks.billing.states.BillingState
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.TransactionBuilder

@InitiatedBy(AttachUnspentChipsFlow::class)
class AttachUnspentChipsFlowResponder(private val session: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        session.receive<String>()
        val billingStates = serviceHub.vaultService.queryBy<BillingState>().states.filter { it.state.data.issuer == session.counterparty }
        if (billingStates.isEmpty()) {
            throw FlowException("No BillingStates have been found for issuer=${session.counterparty}")
        }
        if (billingStates.size > 1) {
            throw FlowException("More than one BillingState has been found for issuer=${session.counterparty}")
        }
        val billingState = billingStates.single()

        val unspentChips = serviceHub.vaultService.queryBy<BillingChipState>().states.filter { it.state.data.billingStateLinearId == billingState.state.data.linearId}
        if (!unspentChips.isEmpty()) {
            val notary = serviceHub.networkMapCache.notaryIdentities.single()
            val builder = TransactionBuilder(notary)
                    .addInputState(billingState)
                    .addCommand(BillingContract.Commands.AttachBack(), ourIdentity.owningKey)
            var totalUnspent = 0L
            unspentChips.forEach {
                builder.addInputState(it)
                totalUnspent += it.state.data.amount
            }
            builder.addOutputState(billingState.state.data.copy(spent = billingState.state.data.spent - totalUnspent), BillingContract.CONTRACT_NAME)

            builder.verify(serviceHub)

            val stx = serviceHub.signInitialTransaction(builder)

            val allSignedTx = subFlow(CollectSignaturesFlow(stx, listOf(session)))
            val finalisedTx = subFlow(FinalityFlow(allSignedTx, listOf(session)))

        }

    }
}