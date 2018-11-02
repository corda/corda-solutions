package net.corda.cordaupdates.transport

import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.spi.connector.transport.Transporter
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transfer.NoTransporterException

/**
 * Implementation of Maven Resolver's [TransporterFactory] for Corda transport.
 *
 * Corda transport expects a repository URL to be specified in the format of "corda:x500Name", i.e. for example "corda:O=BNO,L=New York,C=US"
 */
class CordaTransporterFactory : TransporterFactory {
    companion object {
        const val CORDA_FLOWS_TRANSPORT = "corda"
    }

    override fun getPriority() = 5.0f

    override fun newInstance(session : RepositorySystemSession, repository : RemoteRepository) : Transporter {
        return when(repository.protocol.toLowerCase()) {
            CORDA_FLOWS_TRANSPORT -> FlowsTransporter(session, repository)
            else -> throw NoTransporterException(repository)
        }
    }
}