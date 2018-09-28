package net.corda.cordaupdates.transport.flows

import net.corda.businessnetworks.cordaupdates.core.CordaMavenResolver
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.User
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue

class MavenOverFRPCTests {
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
    fun testRpc() {
        genericTest {
            val resolver = CordaMavenResolver.create(
                    remoteRepoUrl = "rpc:O=BNO,L=New York,C=US",
                    localRepoPath = nodeLocalRepoPath.toAbsolutePath().toString())
            val result = resolver.downloadVersion("net.example:test-artifact:1.5", configProps = mapOf(
                    Pair(ConfigurationProperties.RPC_HOST, participantNode.rpcAddress.host),
                    Pair(ConfigurationProperties.RPC_PORT, participantNode.rpcAddress.port),
                    Pair(ConfigurationProperties.RPC_USERNAME, "test"),
                    Pair(ConfigurationProperties.RPC_PASSWORD, "test")
            ))
            assertTrue(nodeLocalRepoPath.resolve("net/example/test-artifact/1.5/test-artifact-1.5.jar").toFile()!!.exists())
            assertTrue(nodeLocalRepoPath.resolve("net/example/test-artifact/1.5/test-artifact-1.5.pom").toFile()!!.exists())
        }
    }
}