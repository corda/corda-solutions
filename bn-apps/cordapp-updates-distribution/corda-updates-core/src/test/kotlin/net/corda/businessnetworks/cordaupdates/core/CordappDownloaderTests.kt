package net.corda.businessnetworks.cordaupdates.core

import org.junit.Test
import kotlin.test.assertEquals

class CordappDownloaderTests {
    companion object {
        val LOCAL_REPO_PATH = "./TestLocalRepo"
    }

    @Test
    fun simpleTest() {
        val downloader = CordappDownloader("file://${CordappDownloaderTests::class.java.classLoader.getResource("TestRepo").file!!}", LOCAL_REPO_PATH)
        val versions = downloader.listVersions("net.example:test-artifact:[0,)")
        assertEquals(versions.versions!!.map { it.toString() }.toSet(), setOf("0.1", "0.5", "1.0", "1.5", "2.0"))
    }
}