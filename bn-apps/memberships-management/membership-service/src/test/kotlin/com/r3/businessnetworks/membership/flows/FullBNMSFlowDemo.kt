package com.r3.businessnetworks.membership.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.flows.member.GetMembershipsFlow
import com.r3.businessnetworks.membership.states.SimpleMembershipMetadata
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.utilities.getOrThrow
import org.junit.Test
import kotlin.test.fail
import com.r3.businessnetworks.membership.flows.member.Utils.ofType
import net.corda.core.identity.CordaX500Name
import java.lang.Exception
import kotlin.test.assertEquals

/**
 * This is a demo of the Business Network Membership Service. The test demonstrates how a participant can request to join a Business Network
 * and then interact with other Business Network members. The test also demonstrates how the BNO can activate / suspend memberships
 */
class FullBNMSFlowDemo : AbstractFlowTest(
        numberOfBusinessNetworks = 1,
        numberOfParticipants = 5) {
    @Test
    fun demo() {
        val bnoNode = bnoNodes.first()

        // participant who would like to join the Business Network
        val newJoiner = participantsNodes.first()
        // random participant, not the Business Network member
        val nonMember = participantsNodes[1]

        // participant submits a membership request to the BNO via RequestMembershipFlow
        runRequestMembershipFlow(bnoNode, newJoiner, SimpleMembershipMetadata(role="My new role"))

        // the flow issues MembershipState in PENDING status onto the ledger
        // After the state has been issued, the BNO needs to kick-off their internal KYC / on-boarding procedures, do all the paperwork and etc.
        // Once the participant has gone through the on-boarding, the BNO activates membership via ActivateMembershipFlow
        runActivateMembershipFlow(bnoNode, newJoiner.identity())

        // now the new-joiner can request memberships from the BNO via GetMembershipsFlow. Memberships list contains just a single party
        val memberships = runGetMembershipsListFlow(bnoNode, newJoiner, false).ofType<SimpleMembershipMetadata>()
        assert(memberships.keys.single() == newJoiner.identity())
        assert(memberships[newJoiner.identity()]!!.state.data.membershipMetadata.role == "My new role")

        // not members can't see membership list
        try {
            runGetMembershipsListFlow(bnoNode, nonMember, false)
            fail()
        } catch (ex : FlowException) {
            // pass
        }

        // Business Network members can amend their membership metadata via AmendMembershipMetadataFlow
        runAmendMetadataFlow(bnoNode, newJoiner, SimpleMembershipMetadata(role="Some other role"))

        // BNO can suspend memberships via SuspendMembershipFlow
        runSuspendMembershipFlow(bnoNode, newJoiner.identity())

        // suspended members are not able to transact on the Business Network neither can interact with the BNO's node
        try {
            runGetMembershipsListFlow(bnoNode, newJoiner, true)
            fail()
        } catch (ex : FlowException) {
            // pass
        }

        // BNO can re-activate suspended memberships via ActivateMembershipFlow
        runActivateMembershipFlow(bnoNode, newJoiner.identity())

        // Business Network members need to explicitly verify membership of their counterparties, before transacting with them
        val future = nonMember.startFlow(MyInitiatingFlow(newJoiner.identity()))
        mockNetwork.runNetwork()
        try {
            future.getOrThrow()
            fail()
        } catch (ex : Exception) {
            assertEquals("Counterparty must be a member", ex.message)
        }
    }

    @InitiatingFlow
    class MyInitiatingFlow(val counterparty : Party) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            initiateFlow(counterparty).sendAndReceive<String>("Hello!")
        }
    }

    @InitiatedBy(MyInitiatingFlow::class)
    class MyInitiatedFlow(val flowSession : FlowSession) : FlowLogic<Unit>() {
        companion object {
            // name of the well known BNO
            val BNO_NAME = CordaX500Name.parse("O=BNO_0,L=New York,C=US")
        }

        @Suspendable
        override fun call() {
            val bnoParty = serviceHub.identityService.wellKnownPartyFromX500Name(BNO_NAME)!!
            val membership = subFlow(GetMembershipsFlow(bnoParty))[flowSession.counterparty]
            if (membership == null || !membership.state.data.isActive()) {
                throw FlowException("Counterparty must be a member")
            }
            flowSession.receive<String>()
            flowSession.send("Hello!")
        }
    }
}