package com.r3.businessnetworks.membership.flows

import net.corda.core.utilities.getOrThrow
import org.junit.Test
import kotlin.test.fail

class MultipleBusinessNetworksTest : AbstractFlowTest(numberOfBusinessNetworks = 4, numberOfParticipants = 5) {
    @Test
    fun `test interactions with multiple business networks`() {
        val bno1Node = bnoNodes[0]
        val bno2Node = bnoNodes[1]
        val bno3Node = bnoNodes[2]
        val bno4Node = bnoNodes[3]

        val multiBnNode= participantsNodes[0] // participates in all business networks
        val bn1ParticipantNode = participantsNodes[1] // participates in BN1 only
        val bn2ParticipantNode = participantsNodes[2] // participates in BN2 only
        val bn3ParticipantNode = participantsNodes[3] // participates in BN3 only
        val bn4ParticipantNode = participantsNodes[4] // participates in BN4 only

        // registering BN-specific initiated flows
        bn1ParticipantNode.registerInitiatedFlow(BN_1_RespondingFlow::class.java)
        bn2ParticipantNode.registerInitiatedFlow(BN_2_RespondingFlow::class.java)
        bn3ParticipantNode.registerInitiatedFlow(BN_3_RespondingFlow::class.java)
        bn4ParticipantNode.registerInitiatedFlow(BN_4_RespondingFlow::class.java)

        // multiBnNode has the following membership statuses in the business networks
        // BN1 - active
        // BN2 - pending
        // BN3 - suspended
        // BN4 - not a member at all
        runRequestAndActivateMembershipFlow(bno1Node, multiBnNode)
        runRequestMembershipFlow(bno2Node, multiBnNode)
        runRequestAndActivateMembershipFlow(bno3Node, multiBnNode)
        runSuspendMembershipFlow(bno3Node, multiBnNode.identity())

        runRequestAndActivateMembershipFlow(bno1Node, bn1ParticipantNode)
        runRequestAndActivateMembershipFlow(bno2Node, bn2ParticipantNode)
        runRequestAndActivateMembershipFlow(bno3Node, bn3ParticipantNode)
        runRequestAndActivateMembershipFlow(bno4Node, bn4ParticipantNode)

        val future1 = multiBnNode.startFlow(BN_1_InitiatingFlow(bn1ParticipantNode.identity()))
        val future2 = multiBnNode.startFlow(BN_2_InitiatingFlow(bn2ParticipantNode.identity()))
        val future3 = multiBnNode.startFlow(BN_3_InitiatingFlow(bn3ParticipantNode.identity()))
        val future4 = multiBnNode.startFlow(BN_4_InitiatingFlow(bn4ParticipantNode.identity()))

        mockNetwork.runNetwork()

        // future 1 should complete successfully
        future1.getOrThrow()
        // future 2 should fail as multiBnNode's membership is PENDING in BN2
        try {
            future2.getOrThrow()
           fail()
        } catch (ex : NotAMemberException) { }
        // future 3 should fail as multiBnNode's membership is SUSPENDED in BN3
        try {
            future3.getOrThrow()
           fail()
        } catch (ex : NotAMemberException) { }
        // future 4 should fail as multiBnNode's is not a member of BN4
        try {
            future4.getOrThrow()
            fail()
        } catch (ex : NotAMemberException) { }
    }

}