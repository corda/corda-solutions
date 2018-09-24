package net.corda.businessnetworks.cordaupdates.core.flowstransport

import net.corda.core.node.ServiceHub
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transport.file.FileTransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory
import java.lang.IllegalArgumentException

object Utils {
    fun transporterFactory(serviceHub : ServiceHub) : TransporterFactory {
        val configuration = serviceHub.cordaService(BNOConfigurationService::class.java)
        return when (configuration.transport()) {
            "http" -> HttpTransporterFactory()
            "file" -> FileTransporterFactory()
            else -> throw IllegalArgumentException("Unsupported transport ${configuration.transport()}")
        }
    }
}