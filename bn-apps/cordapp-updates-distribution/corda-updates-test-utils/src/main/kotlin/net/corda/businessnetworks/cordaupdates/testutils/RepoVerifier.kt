package net.corda.businessnetworks.cordaupdates.testutils

import java.io.File
import kotlin.test.assertEquals

class RepoVerifier(private val repoPath : String, private val fileExtensions : Set<String> = setOf("jar")) {
    private val tasks = mutableListOf<RepoVerifierTask>()

    // TODO: add support for classifiers
    fun shouldContain(group : String, artifact : String, versions : Set<String>) : RepoVerifier {
        tasks.add(RepoVerifierTask(group, artifact, versions))
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
        val expectedPaths = tasks.flatMap { task ->
            task.versions.flatMap { version ->
                fileExtensions.map { extension -> "$repoPath/${task.group.replace(":", "/")}/${task.artifact}/$version/${task.artifact}-$version.$extension" }
            }
        }.toSet()
        val actualPaths = buildActualPaths(File(repoPath))
        assertEquals(expectedPaths, actualPaths, "Expected contents=$expectedPaths, actual contents=$actualPaths, difference=${if (actualPaths.size > expectedPaths.size) actualPaths - expectedPaths else expectedPaths - actualPaths}")
    }
}

private data class RepoVerifierTask(val group : String,
                                    val artifact : String,
                                    val versions : Set<String>)