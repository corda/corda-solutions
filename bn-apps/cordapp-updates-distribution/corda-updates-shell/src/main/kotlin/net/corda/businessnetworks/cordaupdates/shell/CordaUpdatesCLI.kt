package net.corda.businessnetworks.cordaupdates.shell

import com.github.mustachejava.DefaultMustacheFactory
import net.corda.businessnetworks.cordaupdates.core.ArtifactMetadata
import net.corda.businessnetworks.cordaupdates.core.CordaMavenResolverException
import net.corda.businessnetworks.cordaupdates.core.CordappSyncer
import net.corda.businessnetworks.cordaupdates.core.ResourceNotFoundException
import net.corda.businessnetworks.cordaupdates.core.ResourceTransferException
import net.corda.businessnetworks.cordaupdates.core.SyncerConfiguration
import net.corda.cliutils.CordaCliWrapper
import net.corda.cliutils.ExitCodes
import net.corda.cliutils.start
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.nio.file.Files

private enum class CLICommands {
    INIT, SYNC, PRINT_VERSIONS
}

/**
 * Class that handles CLI interactions. Supports the following modes via --mode flag:
 * - INIT. Initializes an empty local repository and creates a template configuration file.
 *      Uses USER.HOME/.corda-updates folder by default unless a custom location has been specified via --configPath
 * - SYNC. Pulls down missing CorDapps from configured remote repositories. Downloads all versions of all CorDapps from all remote repositories,
 *      that are configured in the settings.yaml file. If a custom CorDapp / version range has been specified via --cordapp flag, the tool will attempt to
 *      find a configuration for it in the settings.yaml and will fail if one doesn't exist.
 * - PRINT_VERSIONS. Prints available versions of a specified CorDapp. Will fail if no CorDapp has been specified.
 */
class CordaUpdatesCLI : CordaCliWrapper("corda-updates", "CLI for corda-updates utility.") {
    companion object {
        val logger : Logger by lazy { LoggerFactory.getLogger("corda-updates") }
        const val CONFIG_FILE_NAME = "settings.yaml"
    }

    //TODO: this is a temporary hack until subcommands are supported: https://r3-cev.atlassian.net/browse/CORDA-1838
    @CommandLine.Option(
            names = ["--mode", "-m"],
            required = true,
            description = ["The execution mode. \${COMPLETION-CANDIDATES}."],
            defaultValue = "SYNC",
            showDefaultValue = CommandLine.Help.Visibility.ALWAYS)
    private lateinit var command : CLICommands

    @CommandLine.Option(
            names = ["--cordapp", "-c"],
            description = ["Coordinates of a CorDapp in standard Maven notation. Supports version ranges i.e. [0,10.1).",
                "If not specified then all CorDapps specified in the $CONFIG_FILE_NAME will be synced."])
    private var cordapp : String? = null

    @CommandLine.Option(
            names = ["--configPath", "-cp"],
            description = ["Path to the $CONFIG_FILE_NAME file. If not specified, the file will be looked for in the current folder and ~/.corda-updates"])
    private var configFile : String? = null

    override fun runProgram() : Int {
        return when (command) {
            CLICommands.INIT -> init()
            CLICommands.SYNC -> sync()
            CLICommands.PRINT_VERSIONS -> printVersions()
        }
    }

    private fun init() : Int {
        logger.info("Launching in ${CLICommands.INIT} mode")

        val basePath = if (configFile == null) File(System.getProperty("user.home"), ".corda-updates") else File(configFile)

        logger.info("Initialising in ${basePath.canonicalPath}")
        if (basePath.exists()) {
            print("Path ${basePath.canonicalPath} already exists. Please remove the folder before initialising, to avoid any of its contents from being overwritten.")
            return ExitCodes.FAILURE
        }

        val repoPath = File(basePath, "repo")

        Files.createDirectories(basePath.toPath())
        Files.createDirectories(repoPath.toPath())

        // creating default settings file from a template
        val mf = DefaultMustacheFactory()
        val mustache = mf.compile("settings.mustache")
        mustache.execute(PrintWriter(FileOutputStream(File(basePath, CONFIG_FILE_NAME))), mapOf(Pair("localRepoPath", repoPath.canonicalPath))).flush()

        print("Initialised in ${basePath.absolutePath}")

        return ExitCodes.SUCCESS
    }

    private fun sync() : Int {
        logger.info("Launching in ${CLICommands.SYNC} mode")
        val syncer = createSyncer() ?: return ExitCodes.FAILURE
        try {
            syncer.syncCordapps(coordinatesWithoutVersion = cordapp).let {
                // We don't need versions that already existed in the local repo. Filtering them out
                val resultsNotFromLocal = it.asSequence().filter { artifact -> artifact.versions.any { version -> !version.isFromLocal } }
                        .map { artifact -> artifact.copy(versions = artifact.versions.filter { version -> !version.isFromLocal }) }.toList()

                if (resultsNotFromLocal.isEmpty()) {
                    println("No CorDapps have been synced")
                } else {
                    println("The following CorDapps have been synced")
                    printResults(resultsNotFromLocal)
                }
            }
        } catch (ex : CordaMavenResolverException) {
            logger.error("Error while syncing CorDapps", ex)
            when (ex) {
                is ResourceNotFoundException -> println("Requested CorDapp has not been found in the remote repository. Use --verbose mode for more details. Exception message: ${ex.message}")
                is ResourceTransferException -> println("Transport exception occurred when tried to download a CorDapp. This might happen if a remote repository url was not specified correctly or if proxy is misconfigured. Use --verbose mode for more details. Exception message: ${ex.message}")
                else -> print("Error while syncing CorDapps: ${ex.message}")
            }
            return ExitCodes.FAILURE
        }
        return ExitCodes.SUCCESS
    }

    private fun createSyncer() : CordappSyncer? {
        val configFile = if (configFile == null) {
            val localConfig = File(CONFIG_FILE_NAME)
            if (localConfig.exists()) localConfig else File(System.getProperty("user.home"), ".corda-updates/$CONFIG_FILE_NAME")
        } else File(configFile)

        if (!configFile.exists()) {
            println("Configuration file has not been found in ${configFile.canonicalPath}. Please use INIT mode to initialise an empty repository.")
            return null
        }

        logger.info("Loading configuration from $configFile")

        val syncerConfig = SyncerConfiguration.readFromFile(configFile)
        return CordappSyncer(syncerConfig, ConsoleRepositoryListener(), ConsoleTransferListener())
    }

    private fun printVersions() : Int {
        if (cordapp == null) {
            println("Please specify a CorDapp to print versions for")
            return ExitCodes.FAILURE
        }
        logger.info("Launching in ${CLICommands.PRINT_VERSIONS} mode")
        val syncer = createSyncer() ?: return ExitCodes.FAILURE
        syncer.getAvailableVersions(cordapp!!).let {
            if (it.isEmpty()) {
                println("No cordapp versions are available")
            } else {
                println("Available versions")
                printResults(it)
            }
        }
        return ExitCodes.SUCCESS
    }
}

fun printResults(results : List<ArtifactMetadata>) =
        results.flatMap { it.toMavenArtifacts() }.forEach {
            println("> $it")
        }

fun main(args : Array<String>) = CordaUpdatesCLI().start(args)