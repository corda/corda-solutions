package com.r3.businessnetworks.billing.states

import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.serialization.CordaSerializable
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

@CordaSerializable
object BillingStateSchemaV1 : MappedSchema(schemaFamily = BillingState::class.java, version = 1, mappedTypes = listOf(PersistentBillingState::class.java)) {
    @Entity
    @Table(name = "billing_states")
    class PersistentBillingState (
            @Column(name = "issuer")
            var issuer: Party,
            @Column(name = "owner")
            var owner : Party,
            @Column(name = "status")
            var status : BillingStateStatus) : PersistentState()
}

@CordaSerializable
object BillingChipStateSchemaV1 : MappedSchema(schemaFamily = BillingChipState::class.java, version = 1, mappedTypes = listOf(PersistentBillingChipState::class.java)) {
    @Entity
    @Table(name = "billing_chip_states")
    class PersistentBillingChipState (
            @Column(name = "issuer")
            var issuer: Party,
            @Column(name = "owner")
            var owner: Party,
            @Column(name = "amount")
            var amount: Long,
            @Column(name = "billing_state_linear_id")
            var billingStateLinearId: String) : PersistentState()
}