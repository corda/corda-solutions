package net.corda.businessnetworks.membership

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.member.GetMembershipsFlow
import net.corda.businessnetworks.membership.states.SimpleMembershipMetadata
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.utilities.getOrThrow
import org.junit.Test
import kotlin.test.fail

/**
 * This is a demo of the Business Network Membership Service. The test demonstrates how a participant can request to join a Business Network
 * and then interact with other Business Network members. The test also demonstrates how the BNO can activate / revoke memberships
 */
class FullBNMSFlowDemo : AbstractFlowTest(5) {
    override fun registerFlows() {
    }

    @Test
    fun demo() {
        // participant who would like to join the Business Network
        val newJoiner = participantsNodes.first()
        // random participant, not the Business Network member
        val nonMember = participantsNodes[1]

        // participant submits a membership request to the BNO via RequestMembershipFlow
        runRequestMembershipFlow(newJoiner, SimpleMembershipMetadata(role="My new role"))

        // the flow issues MembershipState in PENDING status onto the ledger
        // After the state has been issued, the BNO needs to kick-off their internal KYC / on-boarding procedures, do all the paperwork and etc.
        // Once the participant has gone through the on-boarding, the BNO activates membership via ActivateMembershipFlow
        runActivateMembershipFlow(bnoNode, identity(newJoiner))

        // after the membership has been activated, the participant can start transacting on the Business Network.
        // If net.corda.businessnetworks.membership.notificationsEnabled is set to false, then the existing members will see the new-joiner on the next cache refresh,
        // otherwise the BNO will notify the existing members about the new-joiner immediately after membership activation

        // now the new-joiner can request memberships from the BNO via GetMembershipsFlow. Memberships list contains just a single party
        val memberships = runGetMembershipsListFlow(newJoiner, false)
        assert(memberships.keys.single() == identity(newJoiner))
        assert(memberships[identity(newJoiner)]!!.state.data.membershipMetadata.role == "My new role")

        // nodes, that are not the members can't request memberships
        try {
            runGetMembershipsListFlow(nonMember, false)
            fail()
        } catch (ex : FlowException) {
            // pass
        }

        // Business Network members can amend their membership metadata via AmendMembershipMetadataFlow
        runAmendMetadataFlow(newJoiner, SimpleMembershipMetadata(role="Some other role"))

        // BNO can revoke memberships via RevokeMembershipFlow
        runRevokeMembershipFlow(bnoNode, identity(newJoiner))

        // revoked members are not able to transact on the Business Network neither can interact with the BNO's node
        try {
            runGetMembershipsListFlow(newJoiner, true)
            fail()
        } catch (ex : FlowException) {
            // pass
        }

        // BNO can re-activate revoked memberships via ActivateMembershipFlow
        runActivateMembershipFlow(bnoNode, identity(newJoiner))

        // Business Network members need to explicitly verify membership of their counterparties, before transacting with them
        val future = nonMember.startFlow(MyInitiatingFlow(identity(newJoiner)))
        mockNetwork.runNetwork()
        try {
            future.getOrThrow()
            fail()
        } catch (ex : FlowException) {
            assert("Counterparty is not a member" == ex.message)
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
        @Suspendable
        override fun call() {
            val memberships = subFlow(GetMembershipsFlow<SimpleMembershipMetadata>())
            if (!memberships.containsKey(flowSession.counterparty)) {
                throw FlowException("Counterparty is not a member")
            }
            flowSession.receive<String>()
            flowSession.send("Hello!")
        }
    }
}