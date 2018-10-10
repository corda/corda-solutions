package net.corda.cordaupdates.app

import net.corda.cordaupdates.app.bno.GetCordappVersionsForPartyFlow
import net.corda.cordaupdates.app.member.CordappVersionInfo
import net.corda.cordaupdates.app.member.ReportCordappVersionFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.lang.Thread.sleep
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportCordappVersionFlowTest {
    private lateinit var mockNetwork : MockNetwork
    private lateinit var participantANode : StartedMockNode
    private lateinit var participantBNode : StartedMockNode
    private lateinit var bnoNode : StartedMockNode

    @Before
    fun setup() {
        mockNetwork = MockNetwork(cordappPackages = listOf("net.corda.cordaupdates.app.member", "net.corda.cordaupdates.app.bno"),
                notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name.parse("O=Notary,L=London,C=GB"))))
        participantANode = mockNetwork.createPartyNode(CordaX500Name("ParticipantA", "New York", "US"))
        participantBNode = mockNetwork.createPartyNode(CordaX500Name("ParticipantB", "New York", "US"))
        bnoNode = mockNetwork.createPartyNode(CordaX500Name.parse("O=BNO,L=London,C=GB"))
        participantANode.startFlow(ReloadConfigurationFlow("corda-updates-app.conf")).getOrThrow()
        participantBNode.startFlow(ReloadConfigurationFlow("corda-updates-app.conf")).getOrThrow()
    }

    @After
    fun tearDown() {
        mockNetwork.stopNodes()
    }

    @Test
    fun testHappyPath() {
        // each participant reports cordapp version
        executeFlow(participantANode, ReportCordappVersionFlow("com.example.a", "test-artifact-a", "1.0-a"))
        executeFlow(participantBNode, ReportCordappVersionFlow("com.example.b", "test-artifact-b", "1.0-b"))
        val participantAVersion = executeFlow(bnoNode, GetCordappVersionsForPartyFlow(participantANode.stringName())).single()
        val participantBVersion = executeFlow(bnoNode, GetCordappVersionsForPartyFlow(participantBNode.stringName())).single()

        assertEquals(CordappVersionInfo("com.example.a", "test-artifact-a", "1.0-a", participantAVersion.updated), participantAVersion)
        assertEquals(CordappVersionInfo("com.example.b", "test-artifact-b", "1.0-b", participantBVersion.updated), participantBVersion)
    }

    @Test
    fun `should update lastUpdated column value`() {
        executeFlow(participantANode, ReportCordappVersionFlow("com.example.a", "test-artifact-a", "1.0-a"))
        val version1 = executeFlow(bnoNode, GetCordappVersionsForPartyFlow(participantANode.stringName())).single()

        sleep(100)

        executeFlow(participantANode, ReportCordappVersionFlow("com.example.a", "test-artifact-a", "1.0-a"))
        val version2 = executeFlow(bnoNode, GetCordappVersionsForPartyFlow(participantANode.stringName())).single()

        assertTrue (version2.updated > version1.updated)
    }

    @Test
    fun `should update version number if the same cordapp exists`() {
        executeFlow(participantANode, ReportCordappVersionFlow("com.example.a", "test-artifact-a", "1.0-a"))
        executeFlow(participantANode, ReportCordappVersionFlow("com.example.a", "test-artifact-a", "2.0-a"))
        val version2 = executeFlow(bnoNode, GetCordappVersionsForPartyFlow(participantANode.stringName())).single()
        assertEquals ("2.0-a", version2.version)
    }

    @Test
    fun `test multiple cordapps`() {
        executeFlow(participantANode, ReportCordappVersionFlow("com.example.a", "test-artifact-a", "1.0-a"))
        executeFlow(participantANode, ReportCordappVersionFlow("com.example.a", "test-artifact-b", "2.0-a"))
        val versions = executeFlow(bnoNode, GetCordappVersionsForPartyFlow(participantANode.stringName())).map { it.version }.toSet()
        assertEquals (setOf("1.0-a", "2.0-a"), versions)
    }

    private fun StartedMockNode.stringName() = info.legalIdentities.single().name.toString()

    private fun <T> executeFlow(node : StartedMockNode, flowLogic : FlowLogic<T>) : T {
        val future = node.startFlow(flowLogic)
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }
}