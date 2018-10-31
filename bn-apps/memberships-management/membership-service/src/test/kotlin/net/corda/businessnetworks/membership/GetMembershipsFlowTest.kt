package net.corda.businessnetworks.membership

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.bno.GetMembershipListFlowResponder
import net.corda.businessnetworks.membership.member.service.MemberConfigurationService
import net.corda.businessnetworks.membership.member.service.MembershipsCacheHolder
import net.corda.businessnetworks.membership.states.MembershipContract
import net.corda.businessnetworks.membership.states.MembershipState
import net.corda.businessnetworks.membership.states.MembershipStatus
import net.corda.businessnetworks.membership.states.SimpleMembershipMetadata
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
import kotlin.test.assertTrue
import kotlin.test.fail

class GetMembershipsFlowTest : AbstractFlowTest(
        numberOfBusinessNetworks = 2,
        numberOfParticipants = 6,
        participantRespondingFlows = listOf(GetMembershipListFlowResponder::class.java)) {
    @Test
    fun `all nodes should be getting the same list of memberships`() {
        val bnoNode = bnoNodes.first()

        runrequestAndActivateMembershipFlow(bnoNode, participantsNodes)
        val allParties = participantsNodes.identities()

        // verifying memberships list for each party
        participantsNodes.forEach { participantNode ->
            val memberships = runGetMembershipsListFlow(bnoNode, participantNode, true)
            assertEquals(allParties.toSet(), memberships.map { it.value.state.data.member }.toSet())
            val party = participantNode.identity()
            assertEquals(getMembership(participantNode, party), memberships[party])
        }
    }

    @Test
    fun `all memberships should be included into the membership list`() {
        val bnoNode = bnoNodes.first()

        val suspendedNode = participantsNodes[0]
        val pendingNode = participantsNodes[1]
        val okNode = participantsNodes[2]

        runRequestMembershipFlow(bnoNode, participantsNodes)
        // not activating of the memberships to make sure that the pending membership will also appear on the membership list
        runActivateMembershipFlow(bnoNode, (participantsNodes - pendingNode).identities())

        // suspending one of the memberships to make sure that the suspended membership will also appear on the membership list
        runSuspendMembershipFlow(bnoNode, suspendedNode.identity())

        val membershipsSnapshot = runGetMembershipsListFlow(bnoNode, okNode, true)

        val bnoMemberships = getAllMemberships(bnoNode)
        assertEquals(bnoMemberships.toSet(), membershipsSnapshot.values.toSet())
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
        runrequestAndActivateMembershipFlow(bnoNode, participantsNodes)

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
        runrequestAndActivateMembershipFlow(bno1Node, multiBnParticipant)
        runrequestAndActivateMembershipFlow(bno2Node, multiBnParticipant)

        // activating bn1 and bn2 members
        runrequestAndActivateMembershipFlow(bno1Node, bn1Participants)
        runrequestAndActivateMembershipFlow(bno2Node, bn2Participants)

        // membership lists received from BNOs
        val bn1MembershipsList = runGetMembershipsListFlow(bno1Node, multiBnParticipant)
        val bn2MembershipsList = runGetMembershipsListFlow(bno2Node, multiBnParticipant)

        // membership states from BNOs vaults
        val bn1MembershipStates = getAllMemberships(bno1Node)
        val bn2MembershipStates = getAllMemberships(bno2Node)

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
        runrequestAndActivateMembershipFlow(bno1Node, multiBnParticipant, bn1Metadata)
        runrequestAndActivateMembershipFlow(bno2Node, multiBnParticipant, bn2Metadata)

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