package com.r3.businessnetworks.billing.flows.member.service

import com.r3.businessnetworks.billing.states.BillingState
import com.r3.businessnetworks.billing.states.BillingStateSchemaV1
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.Builder.equal
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.SingletonSerializeAsToken

@CordaService
class DatabaseService(private val appServiceHub : AppServiceHub) : SingletonSerializeAsToken() {

    fun getBillinStateByLinearId(linearId : UniqueIdentifier) : StateAndRef<BillingState>? {
        val states = appServiceHub.vaultService
                .queryBy<BillingState>(QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))).states
        return if(states.isEmpty()) null else states.single()
    }

    fun getBillingStatesByIssuer(issuer : Party) : List<StateAndRef<BillingState>> {
        val issuerCriteria = BillingStateSchemaV1.PersistentBillingState::issuer.equal(issuer)
        return appServiceHub.vaultService.queryBy<BillingState>(QueryCriteria.VaultCustomQueryCriteria(issuerCriteria)).states
    }
}