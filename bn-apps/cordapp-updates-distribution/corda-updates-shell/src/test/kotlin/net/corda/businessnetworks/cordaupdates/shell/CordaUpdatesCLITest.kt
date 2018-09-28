package net.corda.businessnetworks.cordaupdates.shell

import net.corda.cliutils.CordaCliWrapper
import net.corda.cliutils.CordaSystemUtils
import net.corda.cliutils.ExitCodes
import net.corda.core.utilities.loggerFor
import org.fusesource.jansi.AnsiConsole
import org.junit.After
import org.junit.Before
import org.junit.Test
import picocli.CommandLine
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CordaUpdatesCLITest {
    companion object {
        private val logger by lazy { loggerFor<CordaUpdatesCLITest>() }
    }

    lateinit var localRepoPath : Path

    @Before
    fun setup() {
        localRepoPath = Files.createTempDirectory("LocalRepo")
    }

    @After
    fun cleanUp() {
        localRepoPath.toFile().deleteRecursively()
    }

    @Test
    fun testDownload() {
        logger.info("")
        val args = arrayOf(
                "--remoteRepoUrl", "file:${File("../TestRepo").canonicalPath}",
                "--localRepoPath", localRepoPath.toAbsolutePath().toString(),
                "--artifact", "net.example:test-artifact:[0.2,1.4]")

        val result = DownloadCommand().runCommand(args)!!

        assertEquals(ExitCodes.SUCCESS, result)
        assertTrue(localRepoPath.resolve("net/example/test-artifact/0.5/test-artifact-0.5.jar").toFile()!!.exists())
        assertTrue(localRepoPath.resolve("net/example/test-artifact/1.0/test-artifact-1.0.jar").toFile()!!.exists())
    }



    fun CordaCliWrapper.runCommand(args : Array<String>) : Int? {
        this.args = args
        AnsiConsole.systemInstall()
        val cmd = CommandLine(this)
        cmd.registerConverter(Path::class.java) { Paths.get(it).toAbsolutePath().normalize() }
        cmd.commandSpec.name(alias)
        cmd.commandSpec.usageMessage().description(description)
        val defaultAnsiMode = if (CordaSystemUtils.isOsWindows()) { CommandLine.Help.Ansi.ON } else { CommandLine.Help.Ansi.AUTO }
        val results = cmd.parseWithHandlers(CommandLine.RunLast().useOut(System.out).useAnsi(defaultAnsiMode),
                CommandLine.DefaultExceptionHandler<List<Any>>().useErr(System.err).useAnsi(defaultAnsiMode),
                *args)
        results?.firstOrNull()?.let {
            return it as Int
        }
        return null

    }
}