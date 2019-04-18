package com.r3.businessnetworks.billing.flows.member.service

import com.r3.businessnetworks.billing.states.*
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.Builder.equal
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.SingletonSerializeAsToken

/**
 * Convenience class to query [BillingState]s and [BillingChipState]s from the vault.
 */
@CordaService
class MemberBillingDatabaseService(private val appServiceHub : AppServiceHub) : SingletonSerializeAsToken() {
    private val me = appServiceHub.myInfo.legalIdentities.single()

    fun getBillingStateByLinearId(linearId : UniqueIdentifier) : StateAndRef<BillingState>? {
        val states = appServiceHub.vaultService
                .queryBy<BillingState>(QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))).states
        return if(states.isEmpty()) null else states.single()
    }

    fun getOurActiveBillingStates() : List<StateAndRef<BillingState>> {
        val ownerCriteria = BillingStateSchemaV1.PersistentBillingState::owner.equal(me)
        val statusCriteria = BillingStateSchemaV1.PersistentBillingState::status.equal(BillingStateStatus.ACTIVE)
        return appServiceHub.vaultService.queryBy<BillingState>(QueryCriteria.VaultCustomQueryCriteria(ownerCriteria)
                .and(QueryCriteria.VaultCustomQueryCriteria(statusCriteria))).states
    }

    fun getOurActiveBillingStatesForExternalId(externalId: String) : List<StateAndRef<BillingState>> {
        val ownerCriteria = BillingStateSchemaV1.PersistentBillingState::owner.equal(me)
        val statusCriteria = BillingStateSchemaV1.PersistentBillingState::status.equal(BillingStateStatus.ACTIVE)
        val categoryCriteria = BillingStateSchemaV1.PersistentBillingState::externalId.equal(externalId)

        return appServiceHub.vaultService.queryBy<BillingState>(QueryCriteria.VaultCustomQueryCriteria(ownerCriteria)
                .and(QueryCriteria.VaultCustomQueryCriteria(statusCriteria))
                .and(QueryCriteria.VaultCustomQueryCriteria(categoryCriteria))).states
    }

    fun getOurActiveBillingStatesByIssuer(issuer : Party) : List<StateAndRef<BillingState>> {
        val issuerCriteria = BillingStateSchemaV1.PersistentBillingState::issuer.equal(issuer)
        // we need to make sure that we are selecting only our states, as the vault might also contain states of other parties
        val ownerCriteria = BillingStateSchemaV1.PersistentBillingState::owner.equal(me)
        val statusCriteria = BillingStateSchemaV1.PersistentBillingState::status.equal(BillingStateStatus.ACTIVE)
        return appServiceHub.vaultService.queryBy<BillingState>(QueryCriteria.VaultCustomQueryCriteria(issuerCriteria)
                .and(QueryCriteria.VaultCustomQueryCriteria(ownerCriteria))
                .and(QueryCriteria.VaultCustomQueryCriteria(statusCriteria))).states
    }

    fun getBillingChipStatesByBillingStateLinearId(billingStateLinearId : UniqueIdentifier) : List<StateAndRef<BillingChipState>> {
        val linearIdCriteria
                = BillingChipStateSchemaV1.PersistentBillingChipState::billingStateLinearId.equal(billingStateLinearId.toString())
        return appServiceHub.vaultService.queryBy<BillingChipState>(QueryCriteria.VaultCustomQueryCriteria(linearIdCriteria)).states
    }

    fun getBillingChipStateByLinearId(chipLinearId : UniqueIdentifier) : StateAndRef<BillingChipState>? {
        val states = appServiceHub.vaultService
                .queryBy<BillingChipState>(QueryCriteria.LinearStateQueryCriteria(linearId = listOf(chipLinearId))).states
        return if(states.isEmpty()) null else states.single()
    }

    fun getOurBillingChipStatesByIssuer(issuer : Party) : List<StateAndRef<BillingChipState>> {
        val issuerCriteria = BillingChipStateSchemaV1.PersistentBillingChipState::issuer.equal(issuer)
        // we need to make sure that we are selecting only our chips, as the vault might also contain chips of other parties
        val ownerCriteria = BillingChipStateSchemaV1.PersistentBillingChipState::owner.equal(me)
        return appServiceHub.vaultService.queryBy<BillingChipState>(QueryCriteria.VaultCustomQueryCriteria(issuerCriteria)
                .and(QueryCriteria.VaultCustomQueryCriteria(ownerCriteria))).states
    }
}