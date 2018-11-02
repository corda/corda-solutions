package net.corda.cordaupdates.transport

import com.sun.xml.internal.messaging.saaj.util.ByteInputStream
import net.corda.cordaupdates.transport.flows.GetResourceFlow
import net.corda.cordaupdates.transport.flows.PeekResourceFlow
import net.corda.core.flows.FlowException
import net.corda.core.node.AppServiceHub
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.getOrThrow
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.spi.connector.transport.AbstractTransporter
import org.eclipse.aether.spi.connector.transport.GetTask
import org.eclipse.aether.spi.connector.transport.PeekTask
import org.eclipse.aether.spi.connector.transport.PutTask
import org.eclipse.aether.spi.connector.transport.Transporter
import org.eclipse.aether.transfer.NoTransporterException
import org.eclipse.aether.util.ConfigUtils

/**
 * Transport over Corda flows. Should be used only within a Corda node. This transporter expects an instance of [AppServiceHub]
 * to be passed via custom session properties. An instance of [AppServiceHub] is required to delegate the data transfer to a separate flow,
 * to avoid checkpointing of Maven Resolver contents as the they are not @Suspendable.
 *
 * When this transport is used from Corda OS, [CordaMavenResolver] should be invoked from a non-flow thread, to prevent the invoking flow from
 * blocking the only thread available to the flows executor. For example.
 *
 * Under the hood [FlowsTransporter] uses [GetResourceFlow] and [PeekResourceFlow] to transfer the content.
 *
 * @CordaService
 * class ResolverExecutor(val appServiceHub : AppServiceHub) : SingletonSerializeAsToken() {
 *     companion object {
 *         val EXECUTOR = Executors.newSingleThreadExecutor()
 *     }
 *
 *     fun invoke(resolver : CordaMavenResolver) {
 *         EXECUTOR.submit {
 *             val additionalProperties = mapOf(Pair(SessionConfigurationProperties.APP_SERVICE_HUB, appServiceHub))
 *             resolver.downloadVersion("net.corda:corda-finance", additionalProperties)
 *         }
 *     }
 * }
 *
 * class MyFlow(val resolver : CordaMavenResolver) : FlowLogic<Unit>() {
 *     @Suspendable
 *     override fun call() {
 *         val executor = serviceHub.cordaService(ResolverExecutor::class.java)
 *         executor.invoke(resolver)
 *     }
 * }
 */
class FlowsTransporter(private val session : RepositorySystemSession,
                       private val repository : RemoteRepository) : AbstractTransporter() {

    private val repoHosterName = repository.url.substring(repository.protocol!!.length + 1, repository.url.length)

    init {
        session.configProperties
        if (repository.protocol.toLowerCase() !in setOf(CordaTransporterFactory.CORDA_FLOWS_TRANSPORT)) {
            throw NoTransporterException(repository)
        }
    }

    override fun implPeek(task : PeekTask?) {
        val appServiceHub = ConfigUtils.getObject(session, null, SessionConfigurationProperties.APP_SERVICE_HUB) as AppServiceHub
        appServiceHub.startFlow(PeekResourceFlow(task!!.location.toString(), repoHosterName)).returnValue.getOrThrow()
    }

    override fun implGet(task : GetTask?) {
        val appServiceHub = ConfigUtils.getObject(session, null, SessionConfigurationProperties.APP_SERVICE_HUB) as AppServiceHub
        val bytes : ByteArray = appServiceHub.startFlow(GetResourceFlow(task!!.location.toString(), repoHosterName)).returnValue.getOrThrow()
        utilGet(task, ByteInputStream(bytes, bytes.size), true, bytes.size.toLong(), false)
    }

    override fun implPut(task : PutTask?) = throw Exception("Flows transport doesn't support PUT")

    override fun classify(error : Throwable?) : Int {
        if (error is ResourceNotFoundException)
            return Transporter.ERROR_NOT_FOUND
        return Transporter.ERROR_OTHER
    }

    override fun implClose() {
    }
}

/**
 * This exception might be thrown by the transporter flows to indicate that the requested content has not been found at the responding side.
 * Maven Resolver distinguishes between resource_not_found exceptions and everything else.
 */
@CordaSerializable
class ResourceNotFoundException : FlowException()