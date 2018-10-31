package net.corda.businessnetworks.membership

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.bno.ActivateMembershipFlow
import net.corda.businessnetworks.membership.bno.ActivateMembershipForPartyFlow
import net.corda.businessnetworks.membership.bno.NotifyMemberFlow
import net.corda.businessnetworks.membership.bno.SuspendMembershipFlow
import net.corda.businessnetworks.membership.bno.SuspendMembershipForPartyFlow
import net.corda.businessnetworks.membership.member.AmendMembershipMetadataFlow
import net.corda.businessnetworks.membership.member.GetMembershipsFlow
import net.corda.businessnetworks.membership.member.RequestMembershipFlow
import net.corda.businessnetworks.membership.bno.service.DatabaseService
import net.corda.businessnetworks.membership.states.MembershipState
import net.corda.businessnetworks.membership.states.SimpleMembershipMetadata
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import java.io.File

/**
 * @param numberOfBusinessNetworks number of BNOs to create. BNOs name is represented with O=BNO_{index of bno},L=New York,C=US, starting from 0
 *      i.e. O=BNO_0,L=New York,C=US, O=BNO_1,L=New York,C=US .... O=BNO_N,L=New York,C=US
 * @param numberOfParticipants *total* number of participants to create. Participants don't have business network membership initially
 * @param participantRespondingFlows responding flows to register for participant nodes
 * @param bnoRespondingFlows responding flows to register for BNO nodes
 */
abstract class AbstractFlowTest(private val numberOfBusinessNetworks : Int,
                                private val numberOfParticipants : Int,
                                private val participantRespondingFlows : List<Class<out FlowLogic<Any>>> = listOf(),
                                private val bnoRespondingFlows : List<Class<out FlowLogic<Any>>> = listOf()) {
    val notaryName = CordaX500Name.parse("O=Notary,L=London,C=GB")
    lateinit var bnoNodes : List<StartedMockNode>
    lateinit var participantsNodes : List<StartedMockNode>
    lateinit var mockNetwork : MockNetwork

    @Before
    open fun setup() {
        mockNetwork = MockNetwork(cordappPackages = listOf("net.corda.businessnetworks.membership",
                "net.corda.businessnetworks.membership.states"),
                notarySpecs = listOf(MockNetworkNotarySpec(notaryName)))
        bnoNodes = (0..numberOfBusinessNetworks).mapIndexed { indexOfBN, _ ->
            val bnoName =  CordaX500Name.parse("O=BNO_$indexOfBN,L=New York,C=US")
            val bnoNode = createNode(bnoName, true)
            bnoRespondingFlows.forEach { bnoNode.registerInitiatedFlow(it) }
            bnoNode
        }

        participantsNodes = (0..numberOfParticipants).map { indexOfParticipant ->
            val node = createNode(CordaX500Name.parse("O=Participant $indexOfParticipant,L=London,C=GB"))
            participantRespondingFlows.forEach { node.registerInitiatedFlow(it) }
            node
        }

        mockNetwork.runNetwork()
    }

    private fun createNode(name : CordaX500Name, isBno : Boolean = false) =
            mockNetwork.createNode(MockNodeParameters(legalName = name))

    @After
    open fun tearDown() {
        mockNetwork.stopNodes()
        NotificationsCounterFlow.NOTIFICATIONS.clear()
    }

    fun runrequestAndActivateMembershipFlow(bnoNode : StartedMockNode, participantNode : StartedMockNode, membershipMetadata : Any = SimpleMembershipMetadata(role = "DEFAULT")) {
        runRequestMembershipFlow(bnoNode, participantNode, membershipMetadata)
        runActivateMembershipFlow(bnoNode, participantNode.identity())
    }

    fun runrequestAndActivateMembershipFlow(bnoNode : StartedMockNode, participantNodes : List<StartedMockNode>, membershipMetadata : Any = SimpleMembershipMetadata(role = "DEFAULT")) {
        runRequestMembershipFlow(bnoNode, participantNodes, membershipMetadata)
        runActivateMembershipFlow(bnoNode, participantNodes.map { it.identity() })
    }

    fun runRequestMembershipFlow(bnoNode : StartedMockNode, participantNode : StartedMockNode, membershipMetadata : Any = SimpleMembershipMetadata(role = "DEFAULT")) : SignedTransaction {
        val future = participantNode.startFlow(RequestMembershipFlow(bnoNode.identity(), membershipMetadata))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    fun runRequestMembershipFlow(bnoNode : StartedMockNode, participantNodes : List<StartedMockNode>, membershipMetadata : Any = SimpleMembershipMetadata(role = "DEFAULT")) : List<SignedTransaction> {
        return participantNodes.map { runRequestMembershipFlow(bnoNode, it, membershipMetadata) }
    }

    fun runSuspendMembershipFlow(bnoNode : StartedMockNode, participant : Party) : SignedTransaction {
        val membership = getMembership(bnoNode, participant)
        val future = bnoNode.startFlow(SuspendMembershipFlow(membership))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    fun runSuspendMembershipForPartyFlow(bnoNode : StartedMockNode, participant : Party) : SignedTransaction {
        val future = bnoNode.startFlow(SuspendMembershipForPartyFlow(participant))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    fun runActivateMembershipFlow(bnoNode : StartedMockNode, participant : Party) : SignedTransaction {
        val membership = getMembership(bnoNode, participant)
        val future = bnoNode.startFlow(ActivateMembershipFlow(membership))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    fun runActivateMembershipFlow(bnoNode : StartedMockNode, participants : List<Party>) : List<SignedTransaction> {
        return participants.map { runActivateMembershipFlow(bnoNode, it) }
    }

    fun runActivateMembershipForPartyFlow(bnoNode : StartedMockNode, participant : Party) : SignedTransaction {
        val future = bnoNode.startFlow(ActivateMembershipForPartyFlow(participant))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    fun runAmendMetadataFlow(bnoNode : StartedMockNode, participantNode : StartedMockNode, newMetadata : Any) : SignedTransaction {
        val future = participantNode.startFlow(AmendMembershipMetadataFlow(bnoNode.identity(), newMetadata))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    fun runGetMembershipsListFlow(bnoNode : StartedMockNode, participantNode : StartedMockNode, force : Boolean = false, filterOutNotExisting : Boolean = true) : Map<Party, StateAndRef<MembershipState<Any>>> {
        val future = participantNode.startFlow(GetMembershipsFlow(bnoNode.identity(), force, filterOutNotExisting))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    fun getMembership(node : StartedMockNode, party : Party) : StateAndRef<MembershipState<Any>> = node.transaction {
        val dbService = node.services.cordaService(DatabaseService::class.java)
        dbService.getMembership(party)!!
    }

    fun getAllMemberships(node : StartedMockNode) : List<StateAndRef<MembershipState<Any>>> = node.transaction {
        val dbService = node.services.cordaService(DatabaseService::class.java)
        dbService.getAllMemberships()
    }

    fun allTransactions(node : StartedMockNode) = node.transaction {
        node.services.validatedTransactions.track().snapshot
    }

    fun fileFromClasspath(fileName : String) = File(AbstractFlowTest::class.java.classLoader.getResource(fileName).toURI())
}

fun StartedMockNode.identity() = info.legalIdentities.single()
fun List<StartedMockNode>.identities() = map { it.identity() }

@InitiatedBy(NotifyMemberFlow::class)
class NotificationsCounterFlow(private val session : FlowSession) : FlowLogic<Unit>() {
    companion object {
        val NOTIFICATIONS : MutableSet<NotificationHolder> = mutableSetOf()
    }

    @Suspendable
    override fun call() {
        val notification  = session.receive<Any>().unwrap { it }
        NOTIFICATIONS.add(NotificationHolder(ourIdentity, session.counterparty, notification))
    }
}

data class NotificationHolder(val member : Party, val bno : Party, val notification : Any)

@InitiatingFlow
class BN_1_InitiatingFlow(counterparty : Party) : AbstractDummyInitiatingFlow(counterparty)
@InitiatingFlow
class BN_2_InitiatingFlow(counterparty : Party) : AbstractDummyInitiatingFlow(counterparty)
@InitiatingFlow
class BN_3_InitiatingFlow(counterparty : Party) : AbstractDummyInitiatingFlow(counterparty)

@InitiatedBy(BN_1_InitiatingFlow::class)
class BN_1_RespondingFlow(session : FlowSession) : AbstractBNAwareRespondingFlow(session, "O=BNO_0,L=New York,C=US")
@InitiatedBy(BN_2_InitiatingFlow::class)
class BN_2_RespondingFlow(session : FlowSession) : AbstractBNAwareRespondingFlow(session, "O=BNO_1,L=New York,C=US")
@InitiatedBy(BN_3_InitiatingFlow::class)
class BN_3_RespondingFlow(session : FlowSession) : AbstractBNAwareRespondingFlow(session, "O=BNO_2,L=New York,C=US")