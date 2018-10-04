package net.corda.businessnetworks.cordaupdates.shell

import net.corda.businessnetworks.cordaupdates.testutils.RepoVerifier
import net.corda.cliutils.CordaCliWrapper
import net.corda.cliutils.CordaSystemUtils
import net.corda.cliutils.ExitCodes
import net.corda.core.utilities.loggerFor
import org.fusesource.jansi.AnsiConsole
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import picocli.CommandLine
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.assertEquals

@Ignore
class CordaUpdatesCLITest {
    companion object {
        private val logger by lazy { loggerFor<CordaUpdatesCLITest>() }
    }

    lateinit var localRepoPath : Path
    lateinit var repoVerifier : RepoVerifier

    @Before
    fun setup() {
        localRepoPath = Files.createTempDirectory("LocalRepo")
        repoVerifier = RepoVerifier(localRepoPath.toString())
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

        val result = SyncCordappsCommand().runCommand(args)!!

        assertEquals(ExitCodes.SUCCESS, result)
        repoVerifier.shouldContain("net:example", "test-artifact", setOf("0.5", "1.0")).verify()
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