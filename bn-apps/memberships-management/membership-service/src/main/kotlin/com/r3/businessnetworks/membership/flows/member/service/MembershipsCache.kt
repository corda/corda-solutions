package com.r3.businessnetworks.membership.flows.member.service

import com.r3.businessnetworks.membership.states.MembershipState
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.Party
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

private typealias MembershipByParty = ConcurrentHashMap<Party, StateAndRef<MembershipState<Any>>>
private typealias MembershipsByBNO = ConcurrentHashMap<Party, MembershipByParty>

/**
 * A singleton service that holds a cache of memberships
 */
@CordaService
class MembershipsCacheHolder(private val appServiceHub: AppServiceHub) : SingletonSerializeAsToken() {
    val cache: MembershipsCache = MembershipsCache()
}

class MembershipsCache {
    private val cache = MembershipsByBNO()
    private val lastRefreshed = ConcurrentHashMap<Party, Instant>()

    fun getMembership(bno: Party, party: Party): StateAndRef<MembershipState<Any>>? = cache[bno]?.get(party)

    fun getMemberships(bno: Party): MembershipByParty = cache[bno] ?: MembershipByParty()

    fun updateMembership(changedMembership: StateAndRef<MembershipState<Any>>) {
        val membershipsByParty = cache.getOrPut(changedMembership.state.data.bno) {
            MembershipByParty()
        }
        membershipsByParty.merge(changedMembership.state.data.member, changedMembership) { oldState, newState ->
            if (oldState.state.data.modified > newState.state.data.modified) oldState else newState
        }
    }

    fun applyMembershipsSnapshot(membershipByParty: List<StateAndRef<MembershipState<Any>>>) {
        if (membershipByParty.isNotEmpty()) {
            val bnos = membershipByParty.asSequence().map { it.state.data.bno }.toSet()
            if (bnos.size != 1) {
                throw IllegalArgumentException("All membership states in the snapshot should refer to the same BNO!")
            }
            val bno = bnos.single()
            membershipByParty.forEach {
                updateMembership(it)
            }
            // don't forget to update the last refreshed time
            lastRefreshed[bno] = Instant.now()
        }
    }

    fun getLastRefreshedTime(bno: Party) = lastRefreshed[bno]

    internal fun reset(bno: Party) {
        cache.remove(bno)
        lastRefreshed.remove(bno)
    }
}