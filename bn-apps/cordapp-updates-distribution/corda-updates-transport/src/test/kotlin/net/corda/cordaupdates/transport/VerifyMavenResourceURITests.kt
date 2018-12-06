package net.corda.cordaupdates.transport

import net.corda.cordaupdates.transport.flows.Utils.verifyMavenResourceURI
import net.corda.core.flows.FlowException
import org.junit.Test

class VerifyMavenResourceURITests {

    @Test
    fun `artifact request should succeed`() = verifyMavenResourceURI("com/google/guava/guava/27.0-jre/guava-27.0-jre-javadoc.jar.asc")

    @Test
    fun `maven metadata request should succeed`() = verifyMavenResourceURI("com/google/guava/maven-metadata.xml")

    @Test(expected = FlowException::class)
    fun `should not contain spaces`() = verifyMavenResourceURI("com/google/guava/guava/27.0-jre/guava-27.0-jre-javadoc.jar.asc ")

    @Test(expected = FlowException::class)
    fun `name not contain double dot`() = verifyMavenResourceURI("com/google/guava/guava/27.0-jre/guava..-27.0-jre-javadoc.jar.asc")

    @Test(expected = FlowException::class)
    fun `no double dots are allowed`() = verifyMavenResourceURI("com/google/guava/guava/27..0-jre/guava-27.0-jre-javadoc.jar.asc")

    @Test(expected = FlowException::class)
    fun `subgroups should not be empty`() = verifyMavenResourceURI("com//guava/guava/27.0-jre/guava-27.0-jre-javadoc.jar.asc")

    @Test(expected = FlowException::class)
    fun `special characters are not allowed`() = verifyMavenResourceURI("com/guava$/guava/27.0-jre/guava-27.0-jre-javadoc.jar.asc")

    @Test(expected = FlowException::class)
    fun `artifact request url should contain at least 4 parts`() = verifyMavenResourceURI("guava/27.0-jre/guava-27.0-jre-javadoc.jar.asc")

    @Test(expected = FlowException::class)
    fun `maven metadata request url should contain at least 2 parts`() = verifyMavenResourceURI("maven-metadata.xml")
}