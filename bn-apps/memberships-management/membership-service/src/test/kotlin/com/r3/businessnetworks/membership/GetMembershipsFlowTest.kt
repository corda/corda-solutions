package com.r3.businessnetworks.membership

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.bno.GetMembershipsFlowResponder
import com.r3.businessnetworks.membership.member.service.MemberConfigurationService
import com.r3.businessnetworks.membership.member.service.MembershipsCacheHolder
import com.r3.businessnetworks.membership.states.MembershipContract
import com.r3.businessnetworks.membership.states.MembershipState
import com.r3.businessnetworks.membership.states.MembershipStatus
import com.r3.businessnetworks.membership.states.SimpleMembershipMetadata
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.TestIdentity
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class GetMembershipsFlowTest : AbstractFlowTest(
        numberOfBusinessNetworks = 2,
        numberOfParticipants = 6,
        participantRespondingFlows = listOf(GetMembershipsFlowResponder::class.java)) {
    @Test
    fun `GetMembershipsFlow should return all active memberships to all business network members`() {
        val bnoNode = bnoNodes.first()

        runRequestAndActivateMembershipFlow(bnoNode, participantsNodes)
        val allParties = participantsNodes.identities()

        // verifying memberships list for each party
        participantsNodes.forEach { participantNode ->
            val memberships = runGetMembershipsListFlow(bnoNode, participantNode, true)
            assertEquals(allParties.toSet(), memberships.map { it.value.state.data.member }.toSet())
            val party = participantNode.identity()
            assertEquals(getMembership(participantNode, party, bnoNode.identity()), memberships[party])
        }
    }

    @Test
    fun `GetMembershipsFlow should not return suspended memberships`() {
        val bnoNode = bnoNodes.first()

        val suspendedNode = participantsNodes[0]
        val okNode = participantsNodes[2]

        runRequestAndActivateMembershipFlow(bnoNode, participantsNodes)
        runSuspendMembershipFlow(bnoNode, suspendedNode.identity())

        assertNull(runGetMembershipsListFlow(bnoNode, okNode)[suspendedNode.identity()])
    }

    @Test
    fun `GetMembershipsFlow should not return pending memberships`() {
        val bnoNode = bnoNodes.first()

        val pendingNode = participantsNodes[1]
        val okNode = participantsNodes[2]

        runRequestMembershipFlow(bnoNode, participantsNodes)
        runActivateMembershipFlow(bnoNode, (participantsNodes - pendingNode).identities())

        assertNull(runGetMembershipsListFlow(bnoNode, okNode, true)[pendingNode.identity()])
    }

    @Test
    fun `GetMembershipsFlow should not return memberships verified by a wrong contract`() {
        val bnoNode = bnoNodes[0]

        val participant1 = participantsNodes[0]
        val participant2 = participantsNodes[1]

        runRequestAndActivateMembershipFlow(bnoNode, listOf(participant2, participant1))

        // reloading configuration with a wrong contract name. All membership states verified by MembershipContract will be rejected
        participant2.services.cordaService(MemberConfigurationService::class.java).reloadConfigurationFromFile(fileFromClasspath("membership-service-with-fake-contract-name.conf"))

        val memberships = runGetMembershipsListFlow(bnoNode, participant2)

        // participant1's membership shouldn't be on the list as it is validated by a wrong contract
        assertNull(memberships[participant1.identity()])
    }

    @Test
    fun `only active members should be able to use this flow`() {
        val bnoNode = bnoNodes.first()

        val suspendedNode = participantsNodes[0]
        val pendingNode = participantsNodes[1]
        val notMember = participantsNodes[3]

        runRequestMembershipFlow(bnoNode, listOf(suspendedNode, pendingNode))
        runSuspendMembershipFlow(bnoNode, suspendedNode.identity())

        try {
            runGetMembershipsListFlow(bnoNode, notMember, true)
            fail()
        } catch (e : NotAMemberException) {
            assertEquals("Counterparty ${notMember.identity()} is not a member of this business network", e.message)
        }
        try {
            runGetMembershipsListFlow(bnoNode, pendingNode, true)
            fail()
        } catch (e : MembershipNotActiveException) {
            assertEquals("Counterparty's ${pendingNode.identity()} membership in this business network is not active", e.message)
        }
        try {
            runGetMembershipsListFlow(bnoNode, suspendedNode, true)
            fail()
        } catch (e : MembershipNotActiveException) {
            assertEquals("Counterparty's ${suspendedNode.identity()} membership in this business network is not active", e.message)
        }
    }

    @Test
    fun `nodes that are not in the Network Map should be filtered out from the list`() {
        val bnoNode = bnoNodes.first()

        // requesting memberships
        runRequestAndActivateMembershipFlow(bnoNode, participantsNodes)

        val participant = participantsNodes.first()
        runGetMembershipsListFlow(bnoNode, participant, true)

        // adding not existing party to the cache
        val notExistingParty = TestIdentity(CordaX500Name.parse("O=Member,L=London,C=GB")).party
        val future = participant.startFlow(AddNotExistingPartyToMembershipsCache(bnoNode.identity(), MembershipState(notExistingParty, bnoNode.identity(), SimpleMembershipMetadata("DEFAULT"), status = MembershipStatus.ACTIVE)))
        mockNetwork.runNetwork()
        future.getOrThrow()

        // not existing parties shouldn't appear on the result list
        val membersWithoutNotExisting = runGetMembershipsListFlow(bnoNode, participant, false, true)
        assertFalse(membersWithoutNotExisting.map { it.value.state.data.member }.contains(notExistingParty))

        // not existing parties should appear on the result list is filterOutNotExisting flag has been explicitly set to false
        val membersWithNotExisting = runGetMembershipsListFlow(bnoNode, participant, false, false)
        assertTrue(membersWithNotExisting.map { it.value.state.data.member }.contains(notExistingParty))
    }

    @Test
    fun `node should maintain separate lists of memberships per business network`() {
        val bno1Node = bnoNodes[0]
        val bno2Node = bnoNodes[1]

        val multiBnParticipant = participantsNodes[0]
        val bn1Participants = participantsNodes.subList(1, 3)
        val bn2Participants = participantsNodes.subList(3, 6)

        // activating multiBnParticipant's node in both of the business networks
        runRequestAndActivateMembershipFlow(bno1Node, multiBnParticipant)
        runRequestAndActivateMembershipFlow(bno2Node, multiBnParticipant)

        // activating bn1 and bn2 members
        runRequestAndActivateMembershipFlow(bno1Node, bn1Participants)
        runRequestAndActivateMembershipFlow(bno2Node, bn2Participants)

        // membership lists received from BNOs
        val bn1MembershipsList = runGetMembershipsListFlow(bno1Node, multiBnParticipant)
        val bn2MembershipsList = runGetMembershipsListFlow(bno2Node, multiBnParticipant)

        // membership states from BNOs vaults
        val bn1MembershipStates = getAllMemberships(bno1Node, bno1Node.identity())
        val bn2MembershipStates = getAllMemberships(bno2Node, bno2Node.identity())

        assertEquals(bn1MembershipStates.map { it.state.data.member }.toSet(), (bn1Participants + multiBnParticipant).map { it.identity() }.toSet())
        assertEquals(bn2MembershipStates.map { it.state.data.member }.toSet(), (bn2Participants + multiBnParticipant).map { it.identity() }.toSet())
        assertEquals(bn1MembershipStates.toSet(), bn1MembershipsList.values.toSet())
        assertEquals(bn2MembershipStates.toSet(), bn2MembershipsList.values.toSet())
    }

    @Test
    fun `nodes should be able to associate different metadata with different business networks`() {
        val bno1Node = bnoNodes[0]
        val bno2Node = bnoNodes[1]

        val multiBnParticipant = participantsNodes[0]

        val bn1Metadata = SomeCustomMembershipMetadata("Hello")
        val bn2Metadata = SimpleMembershipMetadata(role = "BANK", displayedName = "RBS")

        // activating multiBnParticipant's node in both of the business networks
        runRequestAndActivateMembershipFlow(bno1Node, multiBnParticipant, bn1Metadata)
        runRequestAndActivateMembershipFlow(bno2Node, multiBnParticipant, bn2Metadata)

        val bn1MembershipsList = runGetMembershipsListFlow(bno1Node, multiBnParticipant)
        val bn2MembershipsList = runGetMembershipsListFlow(bno2Node, multiBnParticipant)

        // verifying that different business networks can have different metadata associated with the membership states
        assertEquals(bn1Metadata, bn1MembershipsList.values.single().state.data.membershipMetadata)
        assertEquals(bn2Metadata, bn2MembershipsList.values.single().state.data.membershipMetadata)
    }

    @Test(expected = BNONotWhitelisted::class)
    fun `the flow can be run only against whitelisted BNOs`() {
        val bnoNode = bnoNodes.first()
        val participantNode = participantsNodes[0]

        participantNode.services.cordaService(MemberConfigurationService::class.java).reloadConfigurationFromFile(fileFromClasspath("membership-service-without-bno-whitelist.conf"))

        runGetMembershipsListFlow(bnoNode, participantNode)
    }


    @Test
    fun `when membership gets activated after suspension the membership cache should be repopulated with the list of current members`() {
        val bnoNode = bnoNodes.first()

        runRequestAndActivateMembershipFlow(bnoNode, participantsNodes)

        val suspendedMember = participantsNodes.first()
        // populating the memberships cache
        runGetMembershipsListFlow(bnoNode, suspendedMember)

        // suspending membership
        runSuspendMembershipFlow(bnoNode, suspendedMember.identity())

        // the cache now should be empty
        val cache = suspendedMember.services.cordaService(MembershipsCacheHolder::class.java).cache
        assertTrue(cache.getMemberships(bnoNode.identity()).isEmpty())
        assertNull(cache.getLastRefreshedTime(bnoNode.identity()))

        // activating membership
        runActivateMembershipFlow(bnoNode, suspendedMember.identity())

        // making sure that the cache gets repopulated again
        val memberships = runGetMembershipsListFlow(bnoNode, suspendedMember, true)
        assertEquals(participantsNodes.identities().toSet(), memberships.map { it.value.state.data.member }.toSet())

        // verifying memberships list for each party
        participantsNodes.forEach { participantNode ->
        }
    }

}

class AddNotExistingPartyToMembershipsCache(val bno : Party, val membership : MembershipState<SimpleMembershipMetadata>) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val cacheHolder = serviceHub.cordaService(MembershipsCacheHolder::class.java)
        val stateAndRef = StateAndRef(TransactionState(membership, MembershipContract.CONTRACT_NAME, serviceHub.networkMapCache.notaryIdentities.single()), StateRef(SecureHash.zeroHash, 0))
        cacheHolder.cache.updateMembership(stateAndRef)
    }
}

@CordaSerializable
data class SomeCustomMembershipMetadata(val someCustomField : String)