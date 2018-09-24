package net.corda.businessnetworks.cordaupdates.core

import net.corda.businessnetworks.cordaupdates.core.flowstransport.FlowsTransporter
import net.corda.core.identity.CordaX500Name
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.utilities.getOrThrow
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.User
import org.eclipse.aether.collection.DependencyCollectionException
import org.eclipse.aether.resolution.ArtifactDescriptorException
import org.eclipse.aether.resolution.ArtifactResolutionException
import org.eclipse.aether.transfer.ArtifactTransferException
import org.junit.After
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class MavenOverFlowsTests {
    companion object {
        const val BNO_LOCAL_REPO_PATH_PREFIX = "TestLocalRepoBNO"
        const val NODE_LOCAL_REPO_PATH_PREFIX = "TestLocalRepoNode"
    }

    lateinit var bnoNode: NodeHandle
    lateinit var participantNode: NodeHandle
    lateinit var bnoLocalRepoPath : Path
    lateinit var nodeLocalRepoPath : Path
//    val rpcAddress = NetworkHostAndPort("0.0.0.0", 15000)
//    var remoteRepoPath = "file://${MavenOverFlowsTests::class.java.classLoader.getResource("TestRepo").file!!}"

    fun test(testFunction : () -> Unit) {
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
            bnoLocalRepoPath = Files.createTempDirectory(BNO_LOCAL_REPO_PATH_PREFIX)
            nodeLocalRepoPath = Files.createTempDirectory(NODE_LOCAL_REPO_PATH_PREFIX)
            FlowsTransporter.PORT = participantNode.rpcAddress.port
            FlowsTransporter.HOST = participantNode.rpcAddress.host

            testFunction()
        }
    }

    @After
    fun tearDown() {
        bnoLocalRepoPath.toFile().deleteOnExit()
    }

    @Test
    fun simpleTest() {
        test {
//            val participantRpc = CordaRPCClient(participantNode.rpcAddress)
//            val participantProxy  = participantRpc.start("test1", "test1").proxy
//            val handle = participantProxy.startFlowDynamic(TestFlow::class.java, "flow:O=BNO,L=New York,C=US", nodeLocalRepoPath.toAbsolutePath().toString(), "net.example:test-artifact:1.5")
//            handle.returnValue.getOrThrow()

            val resolver = CordaMavenResolver("flow:O=BNO,L=New York,C=US", nodeLocalRepoPath.toAbsolutePath().toString())
            resolver.downloadVersion("net.example:test-artifact:1.5")

        }
    }
}


class ExampleRPCSerializationWhitelist : SerializationWhitelist {
    // Add classes like this.
    override val whitelist = listOf(DependencyCollectionException::class.java,
            ArtifactDescriptorException::class.java,
            ArtifactResolutionException::class.java,
            ArtifactTransferException::class.java)
}