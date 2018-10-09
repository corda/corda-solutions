package net.corda.cordaupdates.transport

import net.corda.cordaupdates.transport.Transports.CORDA_AUTO
import net.corda.cordaupdates.transport.Transports.CORDA_FLOWS
import net.corda.cordaupdates.transport.Transports.CORDA_RPC
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.spi.connector.transport.Transporter
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transfer.NoTransporterException

/**
 * Implementation of Maven Resolver's [TransporterFactory] for Corda transports.
 * Supports corda-rpc, corda-flows and corda-auto transports.
 *
 * corda-flows allows to transfer data over Corda flows. Should be used only when the library is invoked from within a Corda node.
 *
 * corda-rpc allows to transfer data over Corda RPC. It reuses the same flows as corda-flows transport, but invokes them via RPC instead.
 * Should be used when the library s invoked from the outside of a Corda node, i.e. from a shell or a third-party application.
 *
 * corda-auto is an automatic switch between corda-rpc and corda-flows transports. It returns a transport implementation based on the
 * value of "corda-updates.mode " frm custom session properties. The main purpose of this mode is to allow corda-updates to reuse the same
 * configuration, regardless of whether the library is invoked from.
 *
 * Corda-based transports expect a repository URL to be specified in the format of "transport-name:x500Name", i.e. for example "corda-auto:O=BNO,L=New York,C=US"
 */
class CordaTransporterFactory : TransporterFactory {
    override fun getPriority() = 5.0f

    override fun newInstance(session : RepositorySystemSession, repository : RemoteRepository) : Transporter {
        val protocol = repository.protocol.toLowerCase()
        return when {
            CORDA_RPC == protocol -> RPCTransporter(session, repository)
            CORDA_FLOWS == protocol -> FlowsTransporter(session, repository)
            CORDA_AUTO == protocol -> {
                val mode = System.getProperty(ConfigurationProperties.MODE) ?: throw CordaTransportException("${ConfigurationProperties.MODE} property has to be specified for ")
                when (mode) {
                    CORDA_RPC -> RPCTransporter(session, repository)
                    CORDA_FLOWS -> FlowsTransporter(session, repository)
                    else -> throw NoTransporterException(repository)
                }
            }
            else -> throw NoTransporterException(repository)
        }
    }
}

object Transports {
    const val CORDA_RPC = "corda-rpc"
    const val CORDA_FLOWS = "corda-flows"
    const val CORDA_AUTO = "corda-auto"
}

class CordaTransportException(message : String) : Exception(message)
