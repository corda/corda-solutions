package net.corda.businessnetworks.membership.bno.service

import net.corda.businessnetworks.membership.states.Membership
import net.corda.businessnetworks.membership.states.MembershipStateSchemaV1
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.serialization.SingletonSerializeAsToken

@CordaService
class DatabaseService(val serviceHub : ServiceHub) : SingletonSerializeAsToken() {
    companion object {
        // the table with pending membership request. See RequestMembershipFlow for more details
        const val PENDING_MEMBERSHIP_REQUESTS_TABLE = "pending_membership_requests"
        const val PENDING_MEMBER_COLUMN = "pending_member"
    }

    init {
        // Create table if it doesn't already exist.
        val nativeQuery = """
                create table if not exists $PENDING_MEMBERSHIP_REQUESTS_TABLE (
                    $PENDING_MEMBER_COLUMN varchar(255) not null
                );
                create unique index if not exists ${PENDING_MEMBER_COLUMN}_index on $PENDING_MEMBERSHIP_REQUESTS_TABLE($PENDING_MEMBER_COLUMN);
            """
        val session = serviceHub.jdbcSession()
        session.prepareStatement(nativeQuery).execute()
    }

    fun getMembership(member : Party) : StateAndRef<Membership.State>? {
        val memberEqual
                = builder { MembershipStateSchemaV1.PersistentMembershipState::member.equal(member) }
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(QueryCriteria.VaultCustomQueryCriteria(memberEqual))
        val states = serviceHub.vaultService.queryBy<Membership.State>(criteria).states
        return if (states.isEmpty()) null else (states.sortedBy { it.state.data.modified }.last())
    }

    fun getAllMemberships() = serviceHub.vaultService.queryBy<Membership.State>().states
    fun getActiveMemberships() = getAllMemberships().filter { it.state.data.isActive() }


    fun createPendingMembershipRequest(party : Party) {
        val nativeQuery = """
                insert into $PENDING_MEMBERSHIP_REQUESTS_TABLE ($PENDING_MEMBER_COLUMN)
                values (?)
            """
        val session = serviceHub.jdbcSession()

        val statement = session.prepareStatement(nativeQuery)
        statement.setString(1, party.name.toString())
        statement.executeUpdate()
    }


    fun deletePendingMembershipRequest(party : Party) {
        val nativeQuery = """
                delete from $PENDING_MEMBERSHIP_REQUESTS_TABLE
                where $PENDING_MEMBER_COLUMN = ?
            """
        val session = serviceHub.jdbcSession()
        val statement = session.prepareStatement(nativeQuery)
        statement.setString(1, party.name.toString())
        statement.executeUpdate()
    }
}