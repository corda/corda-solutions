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
     * the resource has been found or not in the remote repository
     */
    fun toCordaException(mavenResolverException : Exception, transporter : Transporter) : FlowException {
        val exType = transporter.classify(mavenResolverException)
        return when (exType) {
            Transporter.ERROR_NOT_FOUND -> ResourceNotFoundException()
            else -> FlowException("Error getting resource: ${mavenResolverException.message}")
        }
    }

    /**
     * Maven resource URIs that are received from remote nodes have to be validated against a whitelisted character set to prevent
     * server-side request forgery
     */
    fun verifyMavenResourceURI(uri : String) {
        // splitting the URI
        val split = uri.split("/")
        // should be at least 4 parts - group, name, version, file name
        if (split.size < 4) {
            throw FlowException("Invalid URI $uri")
        }
        // no double dots are allowed
        if (uri.contains("..")) {
            throw FlowException("Invalid URI $uri")
        }

        // artifact name + version + type is the last item of the split
        val nameVersionType = split.last()
        // version is one before the last
        val version = split[split.size - 2]
        // name is two before the last
        val name = split[split.size - 3]

        val alphaNumericalCharacter = "abcdefghijklmnopqrstuvwxyz0123456789".toSet()
        val allowedSpecialCharacters = setOf('-', '.')

        // name version type all together. It's a challenge to split them out, see this for example: https://repo1.maven.org/maven2/com/google/guava/guava/27.0-jre/
        if (nameVersionType.isEmpty()
                || !(alphaNumericalCharacter + allowedSpecialCharacters).containsAll(nameVersionType.toLowerCase().toSet())
                || !nameVersionType.startsWith("$name-$version")) {
            throw FlowException("Invalid URI $uri")
        }
        // verify name
        if (name.isEmpty()
                || !(alphaNumericalCharacter + '-').containsAll(name.toLowerCase().toSet())) {
            throw FlowException("Invalid URI $uri")
        }
        // verify version
        if (version.isEmpty()
                || !(alphaNumericalCharacter + allowedSpecialCharacters).containsAll(version.toLowerCase().toSet())) {
            throw FlowException("Invalid URI $uri")
        }
        // verify each part of the group.
        val group = split.subList(0, split.size - 3)
        for (part in group) {
            if (part.isEmpty() || !alphaNumericalCharacter.containsAll(part.toLowerCase().toSet())) {
                throw FlowException("Invalid URI $uri")
            }
        }
    }
}