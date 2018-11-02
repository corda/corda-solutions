package net.corda.cordaupdates.transport

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.cordaupdates.core.CordaMavenResolver
import net.corda.businessnetworks.cordaupdates.testutils.RepoVerifier
import net.corda.cordaupdates.transport.flows.GetResourceFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
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
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.fail

class MavenOverFlowsTests {
    private lateinit var repoHosterNode: NodeHandle
    private lateinit var participantNode: NodeHandle
    private lateinit var nodeLocalRepoPath : Path
    private lateinit var repoVerifier : RepoVerifier

    fun genericTest(configName : String, testFunction : () -> Unit) {
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
            nodeLocalRepoPath = Files.createTempDirectory("FakeRepo")
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
    fun testFlows() {
        genericTest("corda-updates-app.conf") {
            participantNode.rpc.startFlowDynamic(TestFlow::class.java,
                    "corda:O=Repo Hoster,L=New York,C=US",
                    nodeLocalRepoPath.toAbsolutePath().toString(),
                    "net.example:test-artifact:1.5").returnValue.getOrThrow()
            // let the flow to finish its job as it runs asynchronously
            sleep(5000)
            repoVerifier.shouldContain("net:example", "test-artifact", setOf("1.5")).verify()
        }
    }


    @Test
    fun testSessionFilters() {
        // should fail with deny filter
        genericTest ("corda-updates-with-deny-filter.conf") {
            try {
                participantNode.rpc.startFlowDynamic(GetResourceFlow::class.java,
                        "net/example/test-artifact/1.5/test-artifact-1.5.pom",
                        "O=Repo Hoster,L=New York,C=US").returnValue.getOrThrow()
                fail("Operation should have failed")
            } catch (ex : FlowException) {
                // do nothing
            }
        }

        // should pass with allow all filter
        genericTest ("corda-updates-with-allow-filter.conf") {
            participantNode.rpc.startFlowDynamic(GetResourceFlow::class.java,
                    "net/example/test-artifact/1.5/test-artifact-1.5.pom",
                    "O=Repo Hoster,L=New York,C=US").returnValue.getOrThrow()
        }
    }
}

@StartableByRPC
class TestFlow(private val remoteRepoUrl : String, private val localRepoPath : String, val mavenCoords : String) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        logger.info("Starting TestFlow")
        val executor = serviceHub.cordaService(ExecutorService::class.java)
        executor.downloadVersionAsync(remoteRepoUrl, localRepoPath, mavenCoords)
    }
}


@CordaService
class ExecutorService(private val appServiceHub : AppServiceHub) : SingletonSerializeAsToken() {
    companion object {
        val executor = Executors.newSingleThreadExecutor()!!
    }

    fun downloadVersionAsync(remoteRepoUrl : String, localRepoPath : String, mavenCoords : String) {
        executor.submit(Callable {
            val resolver = CordaMavenResolver.create(remoteRepoUrl = remoteRepoUrl, localRepoPath = localRepoPath)
            resolver.downloadVersion(mavenCoords, configProps = mapOf(Pair(SessionConfigurationProperties.APP_SERVICE_HUB, appServiceHub)))
        })
    }
}