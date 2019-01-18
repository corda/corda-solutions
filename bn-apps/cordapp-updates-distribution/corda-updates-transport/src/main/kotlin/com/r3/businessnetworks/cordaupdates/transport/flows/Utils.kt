package com.r3.businessnetworks.cordaupdates.transport.flows

import com.r3.businessnetworks.cordaupdates.transport.ResourceNotFoundException
import net.corda.core.flows.FlowException
import org.eclipse.aether.spi.connector.transport.Transporter
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transport.file.FileTransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory
import java.lang.Exception
import java.lang.IllegalArgumentException

object Utils {
    // max allowed length for maven URIs
    private const val maxURILength = 500
    // set of alpha numeric characters to verify Maven URIs against
    private val alphaNumericalCharacter = "abcdefghijklmnopqrstuvwxyz0123456789".toSet()
    // set of other special characters allowed in Maven URIs
    private val allowedSpecialCharacters = setOf('-', '.')

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
    fun toCordaException(mavenResolverException : Exception, transporter : Transporter, repository : String, resource : String) : FlowException {
        val exType = transporter.classify(mavenResolverException)
        return when (exType) {
            Transporter.ERROR_NOT_FOUND -> ResourceNotFoundException(repository, resource)
            else -> FlowException("Error getting resource $resource from repository $repository: ${mavenResolverException.message}")
        }
    }

    /**
     * Maven resource URIs that are received from remote nodes have to be validated against a whitelisted character set to prevent
     * server-side request forgery
     */
    fun verifyMavenResourceURI(uri : String) {
        val exceptionMessage = "Invalid URI $uri"

        // don't accept too long strings
        if (uri.length > maxURILength) {
            throw FlowException(exceptionMessage)
        }

        // no double dots are allowed
        if (uri.contains("..")) {
            throw FlowException(exceptionMessage)
        }

        // splitting the URI
        val split = uri.split("/")

        // file name is last part of the split
        val fileName = split.last()
        if (fileName.isEmpty()
                || !(alphaNumericalCharacter + allowedSpecialCharacters).containsAll(fileName.toLowerCase().toSet())) {
            throw FlowException(exceptionMessage)
        }

        // we need to distinguish between artifact and maven metadata requests
        val isMavenMetadataRequest =  fileName.split(".").first() == "maven-metadata"

        // maven metadata requests should consist of at least 3 parts (group, artifact name, file name), while artifact requests - 4 (group, artifact name, version, file name)
        if ((isMavenMetadataRequest && split.size < 3) || (!isMavenMetadataRequest && split.size < 4)) {
            throw FlowException(exceptionMessage)
        }

        // maven-metadata requests don't contain artifact name and artifact version in the URL
        val artifactGroup = if (isMavenMetadataRequest) split.subList(0, split.size - 2) else split.subList(0, split.size - 3)
        // verify each part of the artifact group
        for (part in artifactGroup) {
            if (part.isEmpty() || !alphaNumericalCharacter.containsAll(part.toLowerCase().toSet())) {
                throw FlowException(exceptionMessage)
            }
        }

        // verify artifact name
        val artifactName = if (isMavenMetadataRequest) split[split.size - 2] else split[split.size - 3]
        if (artifactName.isEmpty()
                || !(alphaNumericalCharacter + '-').containsAll(artifactName.toLowerCase().toSet())) {
            throw FlowException(exceptionMessage)
        }

        if (!isMavenMetadataRequest) {
            // version is one before the last
            val artifactVersion = split[split.size - 2]

            // verify version
            if (artifactVersion.isEmpty()
                    || !(alphaNumericalCharacter + allowedSpecialCharacters).containsAll(artifactVersion.toLowerCase().toSet())) {
                throw FlowException(exceptionMessage)
            }

            // name version type all together. It's a challenge to split them out, see this for example: https://repo1.maven.org/maven2/com/google/guava/guava/27.0-jre/
            if (!fileName.startsWith("$artifactName-$artifactVersion")) {
                throw FlowException(exceptionMessage)
            }
        }
    }
}