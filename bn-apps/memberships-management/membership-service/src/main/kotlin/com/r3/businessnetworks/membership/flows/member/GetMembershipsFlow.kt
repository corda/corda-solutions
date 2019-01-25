package com.r3.businessnetworks.membership.flows.member

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.flows.member.service.MembershipsCacheHolder
import com.r3.businessnetworks.membership.flows.member.support.BusinessNetworkAwareInitiatingFlow
import com.r3.businessnetworks.membership.states.MembershipState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

@CordaSerializable
class MembershipListRequest

@CordaSerializable
data class MembershipsListResponse(val memberships : List<StateAndRef<MembershipState<Any>>>)

/**
 * The flow pulls down a list of active members from the BNO. The list is cached via the [MembershipService].
 * The cached list will be reused until it expires or util it gets force-refreshed.
 *
 * @param forceRefresh set to true to request memberships from BNO instead of the local cache
 * @param filterOutMissingFromNetworkMap if true then nodes that are not in the network map will be filtered out from the results list
 *
 * @return a list of ACTIVE memberships validated with a correct Membership Contract specified in the configuration

 * GetMembershipsFlow can be used as follows:
 *
 * @InitiatedBy(SomeInitiatedFlow::class)
 * class MyAuthenticatedFlowResponder(val session : FlowSession) : FlowLogic<Unit>() {
 *     @Suspendable
 *     override fun call() {
 *         val counterpartsMembership = subFlow(GetMembershipsFlow())[session.counterparty]
 *         if (counterpartsMembership == null) {
 *         throw FlowException("Invalid membership")
 *         }
 *         //.....
 *     }
 * }
 */
@InitiatingFlow
@StartableByRPC
open class GetMembershipsFlow(bno : Party, private val forceRefresh : Boolean = false, private val filterOutMissingFromNetworkMap : Boolean = true) : BusinessNetworkAwareInitiatingFlow<Map<Party, StateAndRef<MembershipState<Any>>>>(bno) {

    @Suspendable
    override fun afterBNOIdentityVerified() : Map<Party, StateAndRef<MembershipState<Any>>> {
        val membershipService = serviceHub.cordaService(MembershipsCacheHolder::class.java)
        val cache = membershipService.cache
        val lastRefreshed = cache.getLastRefreshedTime(bno)

        if (forceRefresh || lastRefreshed == null) {
            val bnoSession = initiateFlow(bno)
            val response = bnoSession.sendAndReceive<MembershipsListResponse>(MembershipListRequest()).unwrap { it }
            cache.applyMembershipsSnapshot(response.memberships)
        }

        // filtering out ACTIVE memberships
        val membershipsToReturn : Map<Party, StateAndRef<MembershipState<Any>>> = cache.getMemberships(bno)
                .filterValues { it.state.data.isActive() }

        // filtering out inactive memberships form the result list (the ones that are missing from the network map)
        return if (filterOutMissingFromNetworkMap) membershipsToReturn.filterOutMissingFromNetworkMap() else membershipsToReturn
    }

    private fun Map<Party, StateAndRef<MembershipState<Any>>>.filterOutMissingFromNetworkMap() = filter { serviceHub.networkMapCache.getNodeByLegalIdentity(it.key) != null }
}

@StartableByRPC
open class GetMembersFlow(bno : Party, private val forceRefresh : Boolean = false, private val filterOutNotExisting : Boolean = true) : BusinessNetworkAwareInitiatingFlow<List<PartyAndMembershipMetadata<Any>>>(bno) {
    companion object {
        object GOING_TO_CACHE_OR_BNO : ProgressTracker.Step("Going to cache or BNO for membership data")

        fun tracker() = ProgressTracker(
                GOING_TO_CACHE_OR_BNO
        )
    }

    override val progressTracker = tracker()

    override fun afterBNOIdentityVerified() : List<PartyAndMembershipMetadata<Any>> {
        progressTracker.currentStep = GOING_TO_CACHE_OR_BNO
        return subFlow(GetMembershipsFlow(bno, forceRefresh, filterOutNotExisting)).map { PartyAndMembershipMetadata(it.key, it.value.state.data.membershipMetadata) }
    }
}

@CordaSerializable
data class PartyAndMembershipMetadata<out T>(val party : Party, val membershipMetadata : T)
