package com.r3.businessnetworks.membership.flows.bno.service

import com.google.common.collect.ImmutableList
import com.r3.businessnetworks.membership.states.MembershipState
import com.r3.businessnetworks.membership.states.MembershipStateSchemaV1
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.SingletonSerializeAsToken
import java.io.Serializable
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Table

class PendingMembershipRequestSchema
class PendingMembershipRequestSchemaV1 internal constructor() : MappedSchema(PendingMembershipRequestSchema::class.java, 1, ImmutableList.of(PersistentPendingMembershipRequest::class.java))

@Entity(name = "PersistentPendingMembershipRequest")
@Table(name = "pending_membership_requests")
class PersistentPendingMembershipRequest : Serializable {
    companion object {
        fun from(party : Party) : PersistentPendingMembershipRequest {
            val dbObject = PersistentPendingMembershipRequest()
            dbObject.pendingMember = party.name.toString()
            return dbObject
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    var id : Long = 0

    @Column(name = "pending_member", nullable = false, unique = true)
    var pendingMember : String? = null
}



/**
 * Used by BNO to interact with the underlying database.
 */
@CordaService
class DatabaseService(val serviceHub : ServiceHub) : SingletonSerializeAsToken() {
    fun getMembership(member : Party, bno : Party) : StateAndRef<MembershipState<Any>>? {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(memberCriteria(member))
                .and(bnoCriteria(bno))
        val states = serviceHub.vaultService.queryBy<MembershipState<Any>>(criteria).states
        return if (states.isEmpty()) null else (states.sortedBy { it.state.data.modified }.last())
    }

    fun getAllMemberships(bno : Party) : List<StateAndRef<MembershipState<Any>>> {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(bnoCriteria(bno))
        return serviceHub.vaultService.queryBy<MembershipState<Any>>(criteria).states
    }

    fun getActiveMemberships(bno : Party)
            = getAllMemberships(bno).filter { it.state.data.isActive() }

    /**
     * This method exists to prevent the same member from being able to request a membership multiple times, while their first membership transaction is being processed.
     * Pending membership request is created when a membership request arrives and is deleted when the membership transaction is finalised.
     * Any attempt to create a duplicate pending membership request would violate the DB constraint.
     */
    fun createPendingMembershipRequest(party : Party) {
        serviceHub.withEntityManager {
            persist(PersistentPendingMembershipRequest.from(party))
            flush()
        }
    }

    fun deletePendingMembershipRequest(party : Party) {
        serviceHub.withEntityManager {
            val nativeQuery = """
                delete from PersistentPendingMembershipRequest
                where pendingMember = :pendingMember
            """

            createQuery(nativeQuery)
                    .setParameter("pendingMember", party.name.toString())
                    .executeUpdate()
        }
    }

    private fun memberCriteria(member : Party)
            = QueryCriteria.VaultCustomQueryCriteria(builder { MembershipStateSchemaV1.PersistentMembershipState::member.equal(member) })
    private fun bnoCriteria(bno: Party)
            = QueryCriteria.VaultCustomQueryCriteria(builder { MembershipStateSchemaV1.PersistentMembershipState::bno.equal(bno) })
}