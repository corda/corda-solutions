package com.r3.businessnetworks.membership.states

import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.serialization.CordaSerializable
import java.time.Instant
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

@CordaSerializable
object MembershipStateSchemaV1 : MappedSchema(schemaFamily = MembershipState::class.java, version = 1, mappedTypes = listOf(PersistentMembershipState::class.java)) {
    @Entity
    @Table(name = "membership_states")
    class PersistentMembershipState(
            @Column(name = "member_name")
            var member: Party,
            @Column(name = "bno_name")
            var bno: Party,
            @Column(name = "network_id")
            var networkID: String?,
            @Column(name = "status")
            var status: MembershipStatus,
            @Column(name = "issued")
            var issued: Instant,
            @Column(name = "modified")
            var modified: Instant,
            @Column(name = "linear_id")
            var linearId: UUID) : PersistentState()
}

