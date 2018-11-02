package net.corda.businessnetworks.membership.bno.service

import net.corda.businessnetworks.membership.states.MembershipContract
import net.corda.businessnetworks.membership.states.MembershipState
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

/**
 * Used by BNO to interact with the underlying database.
 */
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

    fun getMembership(member : Party, bno : Party, contract : String) : StateAndRef<MembershipState<Any>>? {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(memberCriteria(member))
                .and(bnoCriteria(bno))
        // filtering out the states validated by a correct contract
        val states = serviceHub.vaultService.queryBy<MembershipState<Any>>(criteria).states.filterByContract(contract)
        return if (states.isEmpty()) null else (states.sortedBy { it.state.data.modified }.last())
    }

    fun getAllMemberships(bno : Party, contract : String) : List<StateAndRef<MembershipState<Any>>> {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(bnoCriteria(bno))
        return serviceHub.vaultService.queryBy<MembershipState<Any>>(criteria).states.filterByContract(contract)
    }

    fun getActiveMemberships(bno : Party, contract : String)
            = getAllMemberships(bno, contract).filter { it.state.data.isActive() }

    /**
     * This method exists to prevent the same member from being able to request a membership multiple times, while their first membership transaction is being processed.
     * Pending membership request is created when a membership request arrives and is deleted when the membership transaction is finalised.
     * Any attempt to create a duplicate pending membership request would violate the DB constraint.
     */
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

    private fun List<StateAndRef<MembershipState<Any>>>.filterByContract(contract : String) = this.filter { it.state.contract == contract }
    private fun memberCriteria(member : Party)
            = QueryCriteria.VaultCustomQueryCriteria(builder { MembershipStateSchemaV1.PersistentMembershipState::member.equal(member) })
    private fun bnoCriteria(bno: Party)
            = QueryCriteria.VaultCustomQueryCriteria(builder { MembershipStateSchemaV1.PersistentMembershipState::bno.equal(bno) })
}