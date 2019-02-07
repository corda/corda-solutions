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
 * The flow is meant to be used by BNOs to issue [BillingState]s on the ledger.
 *
 * @param owner the party to issue the [BillingState] to
 * @param amount the maximum amount of the [BillingChipState]s that can be chipped off
 * @param expiryDate the expiry date of the [BillingState]. Can be null, in which case the [BillingState] will be unexpirable. Transactions that involve [BillingState]s with the expiry dates set must contain time windows.
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