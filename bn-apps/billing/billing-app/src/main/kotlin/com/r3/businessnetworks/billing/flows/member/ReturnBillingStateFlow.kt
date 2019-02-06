package com.r3.businessnetworks.billing.flows.member

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.billing.flows.member.service.MemberDatabaseService
import com.r3.businessnetworks.billing.states.BillingContract
import com.r3.businessnetworks.billing.states.BillingState
import com.r3.businessnetworks.billing.states.BillingStateStatus
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

/**
 * Returns [billingState] to the state issuer. After the state is returned it becomes unusable.
 * Make sure that all unspent chips are attached before returning.
 */
@StartableByRPC
@InitiatingFlow
class ReturnBillingStateFlow(private val billingState : StateAndRef<BillingState>) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call() : SignedTransaction {
        val notary = serviceHub.networkMapCache.notaryIdentities.single()
        val builder = TransactionBuilder(notary)
                .addInputState(billingState)
                .addOutputState(billingState.state.data.copy(status = BillingStateStatus.RETURNED))
                .addCommand(BillingContract.Commands.Return(), billingState.state.data.participants.map { it.owningKey })

        builder.verify(serviceHub)

        val stx = serviceHub.signInitialTransaction(builder)

        val session = initiateFlow(billingState.state.data.issuer)

        val allSignedTx = subFlow(CollectSignaturesFlow(stx, listOf(session)))

        return subFlow(FinalityFlow(allSignedTx, listOf(session)))
    }
}

/**
 * Attaches all unspent chips and returns the [billingState] to the owner
 */
@StartableByRPC
class AttachUnspentChipsAndReturnBillingStateFlow(private val billingState : StateAndRef<BillingState>) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call() : SignedTransaction {
        val result = subFlow(AttachAllUnspentChipsFlow(billingState))
        val databaseService = serviceHub.cordaService(MemberDatabaseService::class.java)
        val billingState = if (result == null) billingState else databaseService.getBillingStateByLinearId(billingState.state.data.linearId)!!
        return subFlow(ReturnBillingStateFlow(billingState))
    }
}