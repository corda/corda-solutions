package net.corda.cordaupdates.transport.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.cordaupdates.core.CordaMavenResolverService
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.User
import org.junit.Test
import java.lang.Thread.sleep
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue

class MavenOverFlowsTests {
    companion object {
        const val BNO_LOCAL_REPO_PATH_PREFIX = "TestLocalRepoBNO"
        const val NODE_LOCAL_REPO_PATH_PREFIX = "TestLocalRepoNode"
    }

    lateinit var bnoNode: NodeHandle
    lateinit var participantNode: NodeHandle
    lateinit var nodeLocalRepoPath : Path

    fun genericTest(testFunction : () -> Unit) {
        val user1 = User("test", "test", permissions = setOf("ALL"))
        val bnoName = CordaX500Name.parse("O=BNO,L=New York,C=US")
        val participantName = CordaX500Name("Participant","New York","US")
        val notaryName = CordaX500Name("Notary", "London","GB")

        driver(DriverParameters(
                extraCordappPackagesToScan = listOf("net.corda.businessnetworks.cordaupdates.core"),
                startNodesInProcess = true,
                notarySpecs = listOf(NotarySpec(notaryName, true)))) {

            bnoNode = startNode(NodeParameters(providedName = bnoName)).getOrThrow()
            participantNode = startNode(NodeParameters(providedName = participantName), rpcUsers = listOf(user1)).getOrThrow()
            nodeLocalRepoPath = Files.createTempDirectory(NODE_LOCAL_REPO_PATH_PREFIX)

            testFunction()

            nodeLocalRepoPath.toFile().deleteRecursively()
        }
    }

    @Test
    fun testFlows() {
        genericTest {
            participantNode.rpc.startFlowDynamic(TestFlow::class.java,
                    "flow:O=BNO,L=New York,C=US",
                    nodeLocalRepoPath.toAbsolutePath().toString(),
                    "net.example:test-artifact:1.5").returnValue.getOrThrow()
            sleep(5000)
            assertTrue(nodeLocalRepoPath.resolve("net/example/test-artifact/1.5/test-artifact-1.5.jar").toFile()!!.exists())
            assertTrue(nodeLocalRepoPath.resolve("net/example/test-artifact/1.5/test-artifact-1.5.pom").toFile()!!.exists())
        }
    }
}

@StartableByRPC
class TestFlow(private val remoteRepoUrl : String, private val localRepoPath : String, val mavenCoords : String) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        logger.info("Starting TestFlow")
        val executor = serviceHub.cordaService(CordaMavenResolverService::class.java)
        executor.downloadVersionAsync(remoteRepoUrl, localRepoPath, mavenCoords)
    }
}