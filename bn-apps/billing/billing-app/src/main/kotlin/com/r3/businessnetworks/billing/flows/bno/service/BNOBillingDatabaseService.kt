package com.r3.businessnetworks.billing.flows.bno.service

import com.r3.businessnetworks.billing.states.BillingState
import com.r3.businessnetworks.billing.states.BillingStateSchemaV1
import com.r3.businessnetworks.billing.states.BillingStateStatus
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.Party
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.Builder.equal
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.SingletonSerializeAsToken

/**
 * Service that simplifies vault queries
 */
@CordaService
class BNOBillingDatabaseService(private val appServiceHub : AppServiceHub) : SingletonSerializeAsToken(){
    fun getBillingStatesByOwnerAndStatus(owner : Party, status : BillingStateStatus) : List<StateAndRef<BillingState>> {
        val ownerCriteria =
                BillingStateSchemaV1.PersistentBillingState::owner.equal(owner)
        val statusCriteria =
                BillingStateSchemaV1.PersistentBillingState::status.equal(status)
        return appServiceHub.vaultService.queryBy<BillingState>(QueryCriteria.VaultCustomQueryCriteria(ownerCriteria)
                .and(QueryCriteria.VaultCustomQueryCriteria(statusCriteria))).states
    }
}