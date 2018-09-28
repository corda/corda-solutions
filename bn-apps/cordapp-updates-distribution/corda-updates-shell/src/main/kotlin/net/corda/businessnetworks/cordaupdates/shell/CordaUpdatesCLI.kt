package net.corda.businessnetworks.cordaupdates.shell

import net.corda.businessnetworks.cordaupdates.core.CordaMavenResolver
import net.corda.cliutils.CordaCliWrapper
import net.corda.cliutils.ExitCodes
import net.corda.core.utilities.loggerFor
import org.eclipse.aether.artifact.DefaultArtifact
import picocli.CommandLine

abstract class AbstractCommand(alias : String, description : String) : CordaCliWrapper(alias, description) {
    companion object {
        val logger = loggerFor<DownloadCommand>()
    }

    @CommandLine.Option(names = ["--remoteRepoUrl", "-r"],
            description = ["Remote repository URL."],
            required = true)
    protected var remoteRepoUrl : String? = null

    @CommandLine.Option(names = ["--localRepoPath", "-l"],
            description = ["Local repository path."],
            required = true)
    protected var localRepoPath : String? = null

    @CommandLine.Option(names = ["--artifact", "-a"],
            description = ["Coordinates of artifact to download in standard Maven notation. Supports version ranges i.e. [0,10.1)."],
            required = true)
    protected var artifact : String? = null

    @CommandLine.Option(names = ["--httpUsername", "-hu"],
            description = ["Username for the remote repository. Support only for HTTP transport."])
    protected var httpUsername : String? = null

    @CommandLine.Option(names = ["--httpPassword", "-hp"],
            description = ["Password for the remote repository. Support only for HTTP transport."])
    protected var httpPassword : String? = null

    @CommandLine.Option(names = ["--httpProxyUrl", "-hpu"],
            description = ["Proxy URL. Support only for HTTP transport."])
    protected var httpProxyUrl : String? = null

    @CommandLine.Option(names = ["--httpProxyType", "-hpt"],
            description = ["Proxy type. HTTP or HTTPS. Support only for HTTP transport."],
            defaultValue = "HTTP")
    protected var httpProxyType : String? = null

    @CommandLine.Option(names = ["--httpProxyPort", "-hpp"],
            description = ["Proxy port. Support only for HTTP transport."])
    protected var httpProxyPort : Int? = null

    @CommandLine.Option(names = ["--httpProxyUsername", "-hpus"],
            description = ["Proxy username. Support only for HTTP transport."])
    protected var httpProxyUsername : String? = null

    @CommandLine.Option(names = ["--httpProxyPassword", "-hppa"],
            description = ["Proxy username. Support only for HTTP transport."])
    protected var httpProxyPassword : String? = null

    @CommandLine.Option(names = ["--rpcHost", "-rh"],
            description = ["RPC host. Support only for RPC transport."])
    protected var rpcHost : String? = null

    @CommandLine.Option(names = ["--rpcPort", "-rp"],
            description = ["RPC port. Support only for RPC transport."])
    protected var rpcPort : String? = null

    @CommandLine.Option(names = ["--rpcUsername", "-ru"],
            description = ["RPC username. Support only for RPC transport."])
    protected var rpcUsername : String? = null

    @CommandLine.Option(names = ["--rpcPassword", "-rpa"],
            description = ["RPC password. Support only for RPC transport."])
    protected var rpcPassword : String? = null

    override fun runProgram() : Int {
        val resolver = CordaMavenResolver.create(
                remoteRepoUrl = remoteRepoUrl,
                localRepoPath = localRepoPath,
                httpUsername = httpUsername,
                httpPassword = httpPassword,
                httpProxyUrl = httpProxyUrl,
                httpProxyType = httpProxyType,
                httpProxyPort = httpProxyPort,
                httpProxyUsername = httpProxyUsername,
                httpProxyPassword = httpProxyPassword,
                rpcHost = rpcHost,
                rpcPort = rpcPort,
                rpcUsername = rpcUsername,
                rpcPassword = rpcPassword
        )

        if (verbose) {
            resolver.repositoryListener = ConsoleRepositoryListener(logger)
            resolver.transferListener = ConsoleTransferListener(logger)
        }

        return invokeResolver(resolver)
    }

    abstract fun invokeResolver(resolver : CordaMavenResolver) : Int
}

class DownloadCommand : AbstractCommand("download", "Download a single artifact version or version range") {
    override fun invokeResolver(resolver : CordaMavenResolver) : Int {
        resolver.downloadVersionRange(artifact!!)
        return ExitCodes.SUCCESS
    }
}

class VersionRangeCommand : AbstractCommand("versionRange", "Print versions within the specified version range") {
    override fun invokeResolver(resolver : CordaMavenResolver) : Int {
        val versionRangeResult = resolver.resolveVersionRange(artifact!!)
        versionRangeResult.versions.forEach {
            val versionedArtifact = DefaultArtifact(artifact).setVersion(it.toString())
            println(versionedArtifact)
        }
        return ExitCodes.SUCCESS
    }
}