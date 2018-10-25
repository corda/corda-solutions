package net.corda.businessnetworks.membership.member.service

import net.corda.businessnetworks.membership.AbstractFlowTest
import net.corda.businessnetworks.membership.member.MembershipsListResponse
import net.corda.businessnetworks.membership.states.MembershipState
import net.corda.businessnetworks.membership.states.SimpleMembershipMetadata
import net.corda.core.contracts.StateAndRef
import net.corda.core.node.services.queryBy
import org.junit.Test
import java.time.Instant
import kotlin.test.assertFalse

class MembershipsCacheTest : AbstractFlowTest(5) {
    override fun registerFlows() = Unit

    @Test
    fun `test membership cache initialisation`() {
        val memberships = initialiseMemberships()
        val cache = MembershipsCache.from(MembershipsListResponse(memberships))

        assert(cache.expires == null)
        assert(cache.membershipMap.map { it.value }.toSet() == memberships.toSet())
        memberships.forEach { assert(it == cache.membershipMap[it.state.data.member]) }
    }

    @Test
    fun `test revoke membership`() {
        val memberships = initialiseMemberships()
        val cache = MembershipsCache.from(MembershipsListResponse(memberships, Instant.now()))

        val nodeToRevoke = participantsNodes.first()
        val partyToRevoke = identity(nodeToRevoke)
        cache.revokeMembership(partyToRevoke)

        assertFalse(cache.membershipMap.containsKey(partyToRevoke))
    }

    @Test
    fun `test update membership`() {
        val memberships = initialiseMemberships()
        val cache = MembershipsCache.from(MembershipsListResponse(memberships, Instant.now()))
        val node = participantsNodes.first()
        val party = identity(node)
        runAmendMetadataFlow(node, SimpleMembershipMetadata(role="New metadata"))

        val newMembership = getMembership(node, party)

        cache.updateMembership(newMembership)

        // the existing collection shouldn't be modified
        assert(cache.membershipMap[party] == newMembership)
    }

    private fun initialiseMemberships() : List<StateAndRef<MembershipState<Any>>> {
        participantsNodes.forEach {
            runRequestMembershipFlow(it)
            runActivateMembershipFlow(bnoNode, identity(it))
        }

        return participantsNodes.map {
            it.transaction { it.services.vaultService.queryBy<MembershipState<Any>>().states }.single()
        }
    }
}