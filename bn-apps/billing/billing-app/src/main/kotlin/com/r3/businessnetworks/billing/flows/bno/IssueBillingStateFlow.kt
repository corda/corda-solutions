package com.r3.businessnetworks.billing.flows.bno

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.billing.flows.bno.service.BNOConfigurationService
import com.r3.businessnetworks.billing.states.BillingContract
import com.r3.businessnetworks.billing.states.BillingState
import com.r3.businessnetworks.billing.states.BillingStateStatus
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.time.Instant

/**
 * The flow is meant to be used by BNO to issue [BillingState] on the ledger.
 *
 * @param owner - member of Business Network to issue states to
 * @param amount - amount of the token to be issued
 *
 * @returns issued [BillingState] with the [SignedTransaction]
 */
@StartableByRPC
@InitiatingFlow
class IssueBillingStateFlow(private val owner : Party,
                            private val amount : Long,
                            private val expiryDate : Instant? = null) : FlowLogic<Pair<BillingState, SignedTransaction>>() {

    @Suspendable
    override fun call() : Pair<BillingState, SignedTransaction> {
        val configuration = serviceHub.cordaService(BNOConfigurationService::class.java)
        val notary = configuration.notaryParty()

        val billingState = BillingState(ourIdentity, owner, amount, 0L, BillingStateStatus.ACTIVE, expiryDate)
        val builder = TransactionBuilder(notary)
                .addOutputState(billingState, BillingContract.CONTRACT_NAME)
                .addCommand(BillingContract.Commands.Issue(), ourIdentity.owningKey, owner.owningKey)

        builder.verify(serviceHub)

        val stx = serviceHub.signInitialTransaction(builder)

        val session = initiateFlow(owner)

        val allSignedTx = subFlow(CollectSignaturesFlow(stx, listOf(session)))

        return Pair(billingState, subFlow(FinalityFlow(allSignedTx, listOf(session))))
    }
}