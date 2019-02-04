package com.r3.businessnetworks.billing.flows.member

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.billing.flows.member.service.DatabaseService
import com.r3.businessnetworks.billing.states.BillingChipState
import com.r3.businessnetworks.billing.states.BillingState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party

@StartableByRPC
class GetBillingStateByLinearId(private val linearId : UniqueIdentifier) : FlowLogic<StateAndRef<BillingState>?>() {
    override fun call() : StateAndRef<BillingState>? {
        val service = serviceHub.cordaService(DatabaseService::class.java)
        return service.getBillingStateByLinearId(linearId)
    }
}

@StartableByRPC
class GetBillingStatesByIssuerFlow(private val issuer : Party) : FlowLogic<List<StateAndRef<BillingState>>>() {
    @Suspendable
    override fun call() : List<StateAndRef<BillingState>> {
        val service = serviceHub.cordaService(DatabaseService::class.java)
        return service.getBillingStatesByIssuer(issuer)
    }
}

@StartableByRPC
class GetChipsByBillingState(private val billingStateByLinearId : UniqueIdentifier) : FlowLogic<List<StateAndRef<BillingChipState>>>() {
    @Suspendable
    override fun call() : List<StateAndRef<BillingChipState>> {
        val service = serviceHub.cordaService(DatabaseService::class.java)
        return service.getChipsByBillingState(billingStateByLinearId)
    }
}

@StartableByRPC
class GetChipsByIssuer(private val issuer : Party) : FlowLogic<List<StateAndRef<BillingChipState>>>() {
    @Suspendable
    override fun call() : List<StateAndRef<BillingChipState>> {
        val service = serviceHub.cordaService(DatabaseService::class.java)
        return service.getChipsByIssuer(issuer)
    }
}