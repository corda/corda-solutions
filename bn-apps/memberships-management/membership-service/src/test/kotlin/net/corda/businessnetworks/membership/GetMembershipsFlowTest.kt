package net.corda.businessnetworks.membership

import net.corda.businessnetworks.membership.bno.GetMembershipListFlowResponder
import net.corda.businessnetworks.membership.common.CounterPartyMembershipNotActive
import net.corda.businessnetworks.membership.common.CounterPartyNotAMemberException
import net.corda.core.flows.FlowException
import org.junit.Test
import kotlin.test.fail

class GetMembershipsFlowTest : AbstractFlowTest(5) {
    override fun registerFlows() {
        bnoNode.registerInitiatedFlow(GetMembershipListFlowResponder::class.java)
    }

    @Test
    fun `all nodes should be getting the same list of members`() {
        participantsNodes.forEach {
            runRequestMembershipFlow(it)
            runActivateMembershipFlow(bnoNode, identity(it))
        }

        val allParties = participantsNodes.map { identity(it) }.toSet()
        participantsNodes.forEach {
            val memberships = runGetMembershipsListFlow(it, true)
            assert(memberships.map { it.value.state.data.member }.toSet() == allParties)
            val party = identity(it)
            assert(memberships[party] == getMembership(it, party))
        }
    }


    @Test
    fun `only active members should be included`() {
        val revokedNode = participantsNodes[0]
        val pendingNode = participantsNodes[1]
        val okNode = participantsNodes[2]

        participantsNodes.forEach {
            runRequestMembershipFlow(it)
            if (it != pendingNode)
                runActivateMembershipFlow(bnoNode, identity(it))
        }
        runRevokeMembershipFlow(bnoNode, identity(revokedNode))

        val memberships = runGetMembershipsListFlow(okNode, true)


        assert(memberships.size == numberOfIdentities - 2)
        assert(memberships.map { it.value.state.data.member }.toSet().none { it == identity(revokedNode) || it == identity(pendingNode) })
    }

    @Test
    fun `only active members should be able to use this flow`() {
        val revokedNode = participantsNodes[0]
        val pendingNode = participantsNodes[1]
        val notMember = participantsNodes[3]

        runRequestMembershipFlow(revokedNode)
        runRequestMembershipFlow(pendingNode)
        runRevokeMembershipFlow(bnoNode, identity(revokedNode))

        try {
            runGetMembershipsListFlow(notMember, true)
            fail()
        } catch (e : CounterPartyNotAMemberException) {
            assert("Counterparty ${identity(notMember)} is not a member of this business network" == e.message)
        }
        try {
            runGetMembershipsListFlow(pendingNode, true)
            fail()
        } catch (e : CounterPartyMembershipNotActive) {
            assert("Counterparty's ${identity(pendingNode)} membership in this business network is not active" == e.message)
        }
        try {
            runGetMembershipsListFlow(revokedNode, true)
            fail()
        } catch (e : CounterPartyMembershipNotActive) {
            assert("Counterparty's ${identity(revokedNode)} membership in this business network is not active" == e.message)
        }
    }
}