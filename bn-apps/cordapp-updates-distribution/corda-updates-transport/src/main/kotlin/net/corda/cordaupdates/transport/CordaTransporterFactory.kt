package net.corda.cordaupdates.transport

import net.corda.cordaupdates.transport.Transports.CORDA_AUTO
import net.corda.cordaupdates.transport.Transports.CORDA_FLOWS
import net.corda.cordaupdates.transport.Transports.CORDA_RPC
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.spi.connector.transport.Transporter
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import java.lang.Exception

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
                    else -> throw CordaTransportException("Unsupported auto transporter mode $mode")
                }
            }
            else -> throw CordaTransportException("Unsupported protocol $protocol")
        }
    }
}

object Transports {
    const val CORDA_RPC = "corda-rpc"
    const val CORDA_FLOWS = "corda-flows"
    const val CORDA_AUTO = "corda-auto"
}

class CordaTransportException(message : String) : Exception(message)
