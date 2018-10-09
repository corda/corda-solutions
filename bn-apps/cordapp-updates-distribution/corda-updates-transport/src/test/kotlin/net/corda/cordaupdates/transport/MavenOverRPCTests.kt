package net.corda.cordaupdates.transport

import net.corda.businessnetworks.cordaupdates.core.CordaMavenResolver
import net.corda.businessnetworks.cordaupdates.testutils.RepoVerifier
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

class MavenOverRPCTests {
    companion object {
        const val NODE_LOCAL_REPO_PATH_PREFIX = "TestLocalRepoNode"
    }

    lateinit var repoHosterNode: NodeHandle
    lateinit var participantNode: NodeHandle
    lateinit var nodeLocalRepoPath : Path
    lateinit var repoVerifier : RepoVerifier

    private fun genericTest(configName : String, testFunction : () -> Unit) {
        val user1 = User("test", "test", permissions = setOf("ALL"))
        val repoHosterName = CordaX500Name.parse("O=Repo Hoster,L=New York,C=US")
        val participantName = CordaX500Name("Participant","New York","US")
        val notaryName = CordaX500Name("Notary", "London","GB")

        driver(DriverParameters(
                extraCordappPackagesToScan = listOf("net.corda.businessnetworks.cordaupdates.core"),
                startNodesInProcess = true,
                notarySpecs = listOf(NotarySpec(notaryName, true)))) {

            repoHosterNode = startNode(NodeParameters(providedName = repoHosterName)).getOrThrow()
            participantNode = startNode(NodeParameters(providedName = participantName), rpcUsers = listOf(user1)).getOrThrow()
            nodeLocalRepoPath = Files.createTempDirectory(NODE_LOCAL_REPO_PATH_PREFIX)
            repoVerifier = RepoVerifier(nodeLocalRepoPath.toString())

            // reloading config first
            repoHosterNode.rpc.startFlowDynamic(ReloadConfigurationFlow::class.java, configName)
                    .returnValue.getOrThrow()

            try {

                testFunction()
            } finally {
                nodeLocalRepoPath.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun testRpc() {
        genericTest("corda-updates.conf") {
            // reloading config first
            repoHosterNode.rpc.startFlowDynamic(ReloadConfigurationFlow::class.java, "corda-updates.properties")
                    .returnValue.getOrThrow()

            val resolver = CordaMavenResolver.create(
                    remoteRepoUrl = "corda-rpc:O=Repo Hoster,L=New York,C=US",
                    localRepoPath = nodeLocalRepoPath.toAbsolutePath().toString())
            val result = resolver.downloadVersion("net.example:test-artifact:1.5", configProps = mapOf(
                    Pair(ConfigurationProperties.RPC_HOST, participantNode.rpcAddress.host),
                    Pair(ConfigurationProperties.RPC_PORT, participantNode.rpcAddress.port),
                    Pair(ConfigurationProperties.RPC_USERNAME, "test"),
                    Pair(ConfigurationProperties.RPC_PASSWORD, "test")
            ))
            repoVerifier.shouldContain("net:example", "test-artifact", setOf("1.5")).verify()
        }
    }
}