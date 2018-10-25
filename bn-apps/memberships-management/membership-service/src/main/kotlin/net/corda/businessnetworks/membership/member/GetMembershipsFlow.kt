package net.corda.businessnetworks.membership.member

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.member.service.MemberConfigurationService
import net.corda.businessnetworks.membership.member.service.MembershipsCache
import net.corda.businessnetworks.membership.member.service.MembershipsCacheHolder
import net.corda.businessnetworks.membership.states.MembershipState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import java.time.Instant

@CordaSerializable
class MembershipListRequest

@CordaSerializable
data class MembershipsListResponse(val memberships: List<StateAndRef<MembershipState<Any>>>, val expires: Instant? = null)

/**
 * The flow pulls down a list of active members from the BNO. The list is cached via the [MembershipService].
 * The cached list will be reused until it expires or util it gets force-refreshed.
 * GetMembershipsFlow can be used as follows:
 *
 * @param forceRefresh set to true to request memberships from BNO instead of the local cache
 * @param filterOutNotExisting if true then nodes that are not in the network map will be filtered out from the results list
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
class GetMembershipsFlow<out T>(private val forceRefresh: Boolean = false, private val filterOutNotExisting : Boolean = true) : FlowLogic<Map<Party, StateAndRef<MembershipState<T>>>>() {
    @Suspendable
    override fun call(): Map<Party, StateAndRef<MembershipState<T>>> {
        val membershipService = serviceHub.cordaService(MembershipsCacheHolder::class.java)
        val cache = membershipService.cache
        val now = serviceHub.clock.instant()

        val membershipsToReturn : Map<Party, StateAndRef<MembershipState<T>>>
        if (forceRefresh || cache == null || if (cache.expires == null) false else cache.expires < (now)) {
            val configuration = serviceHub.cordaService(MemberConfigurationService::class.java)
            val bno = configuration.bnoParty()
            val bnoSession = initiateFlow(bno)
            val response = bnoSession.sendAndReceive<MembershipsListResponse>(MembershipListRequest()).unwrap { it }
            val newCache = MembershipsCache.from(response)
            membershipService.cache = newCache
            membershipsToReturn = newCache.membershipMap.mapValues{ it.value as StateAndRef<MembershipState<T>> }
        } else {
            membershipsToReturn = cache.membershipMap.mapValues { it.value as StateAndRef<MembershipState<T>> }
        }

        return if (filterOutNotExisting) membershipsToReturn.filterOutInactive() else membershipsToReturn
    }

    private fun Map<Party, StateAndRef<MembershipState<T>>>.filterOutInactive() = filter { serviceHub.networkMapCache.getNodeByLegalIdentity(it.key) != null }
}

@StartableByRPC
class GetMembersFlow<out T>(private val forceRefresh : Boolean = false, private val filterOutNotExisting : Boolean = true) : FlowLogic<List<PartyAndMembershipMetadata<T>>>() {

    companion object {
        object GOING_TO_CACHE_OR_BNO : ProgressTracker.Step("Going to cache or BNO for membership data")

        fun tracker() = ProgressTracker(
            GOING_TO_CACHE_OR_BNO
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): List<PartyAndMembershipMetadata<T>> {
        progressTracker.currentStep = GOING_TO_CACHE_OR_BNO
        return subFlow(GetMembershipsFlow<T>(forceRefresh, filterOutNotExisting)).map { PartyAndMembershipMetadata(it.key, it.value.state.data.membershipMetadata) }
    }
}

@CordaSerializable
data class PartyAndMembershipMetadata<out T>(val party : Party, val membershipMetadata: T)
