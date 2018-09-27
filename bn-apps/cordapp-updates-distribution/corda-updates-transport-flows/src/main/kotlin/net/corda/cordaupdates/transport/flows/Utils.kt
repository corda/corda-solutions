package net.corda.cordaupdates.transport.flows

import net.corda.core.flows.FlowException
import org.eclipse.aether.spi.connector.transport.Transporter
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transport.file.FileTransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory
import java.lang.Exception
import java.lang.IllegalArgumentException

object Utils {
    fun transporterFactory(transport : String) : TransporterFactory = when (transport) {
            "http" -> HttpTransporterFactory()
            "file" -> FileTransporterFactory()
            else -> throw IllegalArgumentException("Unsupported transport $transport")
        }

    fun toCordaException(mavenResolverException : Exception, transporter : Transporter) : FlowException {
        val exType = transporter.classify(mavenResolverException)
        return when (exType) {
            Transporter.ERROR_NOT_FOUND -> ResourceNotFoundException()
            else -> FlowException("Error getting resource: ${mavenResolverException.message}")
        }
    }
}