package com.r3.businessnetworks.billing.flows.bno

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.billing.flows.bno.service.BNOConfigurationService
import com.r3.businessnetworks.billing.flows.bno.service.BNODatabaseService
import com.r3.businessnetworks.billing.states.BillingContract
import com.r3.businessnetworks.billing.states.BillingState
import com.r3.businessnetworks.billing.states.BillingStateStatus
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

/**
 * Revokes [billingState]. [BillingState]s are supposed to be revoked as result of a governance action. [BillingStateStatus.REVOKED] [BillingState]s
 * and associated [BillingChipState]s can not be used by the state owners anymore.
 *
 * @param billingState the [BillingState] to revoke
 */
@StartableByRPC
@InitiatingFlow
class RevokeBillingStateFlow(private val billingState : StateAndRef<BillingState>) : FlowLogic<Pair<BillingState, SignedTransaction>>() {

    @Suspendable
    override fun call() : Pair<BillingState, SignedTransaction> {
        if (billingState.state.data.issuer != ourIdentity) throw FlowException("Only state issuer can call this flow")

        val configuration = serviceHub.cordaService(BNOConfigurationService::class.java)
        val notary = configuration.notaryParty()
        val outputState = billingState.state.data.copy(status = BillingStateStatus.REVOKED)
        val builder = TransactionBuilder(notary)
                .addInputState(billingState)
                .addOutputState(outputState, BillingContract.CONTRACT_NAME)
                .addCommand(BillingContract.Commands.Revoke(), ourIdentity.owningKey)

        builder.verify(serviceHub)

        val stx = serviceHub.signInitialTransaction(builder)

        val session = initiateFlow(billingState.state.data.owner)

        return Pair(outputState, subFlow(FinalityFlow(stx, listOf(session))))
    }
}

/**
 * Revokes all [BillingState]s for the given party
 *
 * @param party the party to revoke [BillingState]s of
 */
@StartableByRPC
class RevokeBillingStatesForPartyFlow(val party : Party) : FlowLogic<List<Pair<BillingState, SignedTransaction>>>() {
    @Suspendable
    override fun call() : List<Pair<BillingState, SignedTransaction>> {
        val dbService = serviceHub.cordaService(BNODatabaseService::class.java)
        return dbService.getBillingStatesByOwnerAndStatus(party, BillingStateStatus.ACTIVE).map {
            subFlow(RevokeBillingStateFlow(it))
        }
    }
}