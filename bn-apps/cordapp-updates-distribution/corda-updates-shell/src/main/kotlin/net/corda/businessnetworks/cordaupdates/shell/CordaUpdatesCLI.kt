package net.corda.businessnetworks.cordaupdates.shell

import net.corda.businessnetworks.cordaupdates.core.ArtifactMetadata
import net.corda.businessnetworks.cordaupdates.core.CordappSyncer
import net.corda.businessnetworks.cordaupdates.core.SyncerConfiguration
import net.corda.cliutils.CordaCliWrapper
import net.corda.cliutils.ExitCodes
import picocli.CommandLine

class SyncCordappsCommand : CordaCliWrapper("sync", "Download a single version or a version range of a cordapp.") {
    @CommandLine.Option(names = ["--cordapp", "-c"],
            description = ["Coordinates of a cordapp in standard Maven notation. Supports version ranges i.e. [0,10.1). If not specified - then the all cordapps from config.yaml will be synced."])
    private var cordapp : String? = null

    @CommandLine.Option(names = ["--config", "-cc"],
            description = ["Optional path to the configuration file."])
    private var configFile : String? = null

    override fun runProgram() : Int {
        val syncerConfig = SyncerConfiguration.readFromFileOrDefaultLocations(configFile)
        val syncer = CordappSyncer(syncerConfig)
        syncer.syncCordapps(cordappToSync = cordapp).let {
            println("The following artifacts have been synced")
            printResults(it)
        }
        return ExitCodes.SUCCESS
    }
}

class PrintVersionRangeCommand : CordaCliWrapper("sync", "Print available version range of a cordapp.") {
    @CommandLine.Option(names = ["--cordapp", "-c"],
            description = ["Coordinates of a cordapp in standard Maven notation. Supports version ranges i.e. [0,10.1)."],
            required = true)
    private var cordapp : String? = null

    @CommandLine.Option(names = ["--config", "-cc"],
            description = ["Optional path to the configuration file."])
    private var configFile : String? = null

    override fun runProgram() : Int {
        val syncerConfig = SyncerConfiguration.readFromFileOrDefaultLocations(configFile)
        val syncer = CordappSyncer(syncerConfig)
        syncer.getAvailableVersions(cordapp = cordapp).let {
            println("Available versions")
            printResults(it)
        }
        return ExitCodes.SUCCESS
    }
}

fun printResults(results : List<ArtifactMetadata>) {
    results.flatMap { it.toMavenArtifacts() }.forEach {
        println("> $it")
    }
}
