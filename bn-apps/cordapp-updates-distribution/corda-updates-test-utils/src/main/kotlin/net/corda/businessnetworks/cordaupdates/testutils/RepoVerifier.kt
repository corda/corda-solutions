package net.corda.businessnetworks.cordaupdates.testutils

import java.io.File
import kotlin.test.assertEquals

/**
 * Scans contents of the local Maven repository and verifies them against expectations ([RepoVerifierExpectation]) provided by the user.
 * Contents of the local repository has to match 1-to-1 with the provided expectations, otherwise an exception will be thrown.
 * Typical usage could look like:
 *
 * RepoVerifier("~/.m2/repository")
 *      .shouldContain("net.corda", "corda-finance", setOf("0.1", "0.2"))
 *      .shouldContain("net.corda.businessnetworks", "membership-service", setOf("1.0"))
 *      .verify()
 *
 * This class is intended to be used from tests only.
 */
class RepoVerifier(private val repoPath : String, private val fileExtensions : Set<String> = setOf("jar")) {
    private val expectations = mutableListOf<RepoVerifierExpectation>()

    /**
     * Adds an expectation to the expectations list.
     */
    fun shouldContain(group : String, artifact : String, versions : Set<String>) : RepoVerifier {
        expectations.add(RepoVerifierExpectation(group, artifact, versions))
        return this
    }

    private fun buildActualPaths(root : File) : Set<String> {
        return root.listFiles().flatMap {
            val files = mutableSetOf<String>()
            if (it.isFile
                    && it.extension in fileExtensions
                    && it.name !in setOf("maven-metadata-remote.xml", "resolver-status.properties")) files.add(it.toString())
            if (it.isDirectory) files.addAll(buildActualPaths(it))
            files
        }.toSet()
    }

    fun verify() {
        val expectedPaths = expectations.flatMap { task ->
            task.versions.flatMap { version ->
                fileExtensions.map { extension -> "$repoPath/${task.group.replace(":", "/")}/${task.artifact}/$version/${task.artifact}-$version.$extension" }
            }
        }.toSet()
        val actualPaths = buildActualPaths(File(repoPath))
        assertEquals(expectedPaths, actualPaths, "Expected contents=$expectedPaths, actual contents=$actualPaths, difference=${if (actualPaths.size > expectedPaths.size) actualPaths - expectedPaths else expectedPaths - actualPaths}")
    }
}

/**
 * Wrapper around artifact group, name and a set of versions
 */
private data class RepoVerifierExpectation(val group : String,
                                           val artifact : String,
                                           val versions : Set<String>)