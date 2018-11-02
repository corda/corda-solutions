package net.corda.cordaupdates.transport.flows

import net.corda.cordaupdates.transport.ResourceNotFoundException
import net.corda.core.flows.FlowException
import org.eclipse.aether.spi.connector.transport.Transporter
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transport.file.FileTransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory
import java.lang.Exception
import java.lang.IllegalArgumentException

object Utils {
    /**
     * Creates Maven Resolver transporter factory based on the provided transport name
     */
    fun transporterFactory(transport : String) : TransporterFactory = when (transport) {
            "http" -> HttpTransporterFactory()
            "file" -> FileTransporterFactory()
            else -> throw IllegalArgumentException("Unsupported transport $transport")
        }

    /**
     * Converts Maven Resolver exception to FlowException. The aim is to let the initiating flow know whether
     * the resource has been found or not ib the remote repository
     */
    fun toCordaException(mavenResolverException : Exception, transporter : Transporter) : FlowException {
        val exType = transporter.classify(mavenResolverException)
        return when (exType) {
            Transporter.ERROR_NOT_FOUND -> ResourceNotFoundException()
            else -> FlowException("Error getting resource: ${mavenResolverException.message}")
        }
    }
}