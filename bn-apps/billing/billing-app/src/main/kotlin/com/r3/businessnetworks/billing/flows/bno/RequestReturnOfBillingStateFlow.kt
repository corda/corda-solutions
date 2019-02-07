package com.r3.businessnetworks.billing.flows.bno

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.billing.flows.bno.service.BNODatabaseService
import com.r3.businessnetworks.billing.states.BillingState
import com.r3.businessnetworks.billing.states.BillingStateStatus
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class ReturnRequest(val billingStateLinearId : UniqueIdentifier)

/**
 * Requests a party to return the [BillingState]. Before returning the state, the party wold attach all unspent [BillingChipState] to it.
 * Returned [BillingState]s and the associated [BillingChipState]s can not be used anymore.
 *
 * @param billingState the [BillingState] to return. Must be in [BillingStateStatus.ACTIVE] status.
 */
@InitiatingFlow
@StartableByRPC
class RequestReturnOfBillingStateFlow(private val billingState : StateAndRef<BillingState>) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        if (billingState.state.data.issuer != ourIdentity) throw FlowException("Only state issuer can call this flow")
        val ownerSession = initiateFlow(billingState.state.data.owner)
        ownerSession.send(ReturnRequest(billingState.state.data.linearId))
    }
}

/**
 * Requests a party to return all of their [BillingStateStatus.ACTIVE] [BillingState]s.
 *
 * @param party the party who needs to return all of their [BillingState]s
 */
@StartableByRPC
class RequestReturnOfBillingStateForPartyFlow(private val party : Party) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val databaseService = serviceHub.cordaService(BNODatabaseService::class.java)
        databaseService.getBillingStatesByOwnerAndStatus(party, BillingStateStatus.ACTIVE).forEach {
            subFlow(RequestReturnOfBillingStateFlow(it))
        }
    }
}