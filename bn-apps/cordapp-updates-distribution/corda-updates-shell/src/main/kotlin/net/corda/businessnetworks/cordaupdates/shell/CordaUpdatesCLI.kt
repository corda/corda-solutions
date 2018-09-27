package net.corda.businessnetworks.cordaupdates.shell

import net.corda.businessnetworks.cordaupdates.core.CordaMavenResolver
import net.corda.cliutils.CordaCliWrapper
import net.corda.cliutils.ExitCodes
import net.corda.cordaupdates.transport.flows.ConfigurationProperties
import net.corda.core.utilities.loggerFor
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.repository.Authentication
import org.eclipse.aether.repository.Proxy
import org.eclipse.aether.util.repository.AuthenticationBuilder
import org.slf4j.event.Level
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
        // setting up authentication
        var authentication : Authentication? = null
        if (httpUsername != null && httpPassword != null) {
            authentication = AuthenticationBuilder().addUsername(httpUsername).addPassword(httpPassword).build()
            if (isLogInfo()) {
                logger.info("Using authentication username=$httpUsername password=******")
            }
        }

        // setting up proxy
        var proxy : Proxy? = null
        if (httpProxyUrl != null && httpProxyType != null && httpProxyPort != null) {
            var proxyAuthentication : Authentication? = null
            if (httpProxyPassword != null && httpProxyUsername != null) {
                proxyAuthentication = AuthenticationBuilder().addUsername(httpProxyUsername).addPassword(httpProxyPassword).build()
            }
            proxy = Proxy(httpProxyType, httpProxyUrl, httpProxyPort!!, proxyAuthentication)
        }

        val configurationProperties = mutableMapOf<String, Any>()

        // RPC options
        rpcHost?.let { configurationProperties[ConfigurationProperties.RPC_HOST] = it }
        rpcPort?.let { configurationProperties[ConfigurationProperties.RPC_PORT] = it }
        rpcUsername?.let { configurationProperties[ConfigurationProperties.RPC_USERNAME] = it }
        rpcPassword?.let { configurationProperties[ConfigurationProperties.RPC_PASSWORD] = it }

        val resolver = CordaMavenResolver(remoteRepoUrl!!, localRepoPath!!, authentication, proxy)
        // attaching console loggers only if --verbose flag have been specified
        if (verbose) {
            resolver.repositoryListener = ConsoleRepositoryListener(logger)
            resolver.transferListener = ConsoleTransferListener(logger)
        }

        return invokeResolver(resolver, configurationProperties)
    }

    protected fun isLogInfo() : Boolean = verbose && loggingLevel.toInt() <= Level.INFO.toInt()

    abstract fun invokeResolver(resolver : CordaMavenResolver, configurationProperties: Map<String, Any>) : Int
}

class DownloadCommand : AbstractCommand("download", "Download a single artifact version or version range") {
    override fun invokeResolver(resolver : CordaMavenResolver, configurationProperties: Map<String, Any>) : Int {
        resolver.downloadVersionRange(artifact!!, configurationProperties)
        return ExitCodes.SUCCESS
    }
}

class VersionRangeCommand : AbstractCommand("versionRange", "Print versions within the specified version range") {
    override fun invokeResolver(resolver : CordaMavenResolver, configurationProperties: Map<String, Any>) : Int {
        val versionRangeResult = resolver.resolveVersionRange(artifact!!, configurationProperties)
        versionRangeResult.versions.forEach {
            val versionedArtifact = DefaultArtifact(artifact).setVersion(it.toString())
            println(versionedArtifact)
        }
        return ExitCodes.SUCCESS
    }
}