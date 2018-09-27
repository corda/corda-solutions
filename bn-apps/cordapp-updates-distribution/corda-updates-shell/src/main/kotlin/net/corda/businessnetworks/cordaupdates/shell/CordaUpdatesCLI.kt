package net.corda.businessnetworks.cordaupdates.shell

import net.corda.businessnetworks.cordaupdates.core.CordaMavenResolver
import net.corda.cliutils.CordaCliWrapper
import net.corda.cliutils.CordaVersionProvider
import net.corda.cliutils.ExitCodes
import net.corda.cordaupdates.transport.flows.ConfigurationProperties
import net.corda.core.utilities.loggerFor
import org.eclipse.aether.repository.Authentication
import org.eclipse.aether.repository.Proxy
import org.eclipse.aether.util.repository.AuthenticationBuilder
import org.slf4j.event.Level
import picocli.CommandLine



@CommandLine.Command(mixinStandardHelpOptions = true,
        versionProvider = CordaVersionProvider::class,
        sortOptions = false,
        showDefaultValues = true,
        synopsisHeading = "%n@|bold,underline Usage|@:%n%n",
        descriptionHeading = "%n@|bold,underline Description|@:%n%n",
        parameterListHeading = "%n@|bold,underline Parameters|@:%n%n",
        optionListHeading = "%n@|bold,underline Options|@:%n%n",
        commandListHeading = "%n@|bold,underline Commands|@:%n%n")
class CordaUpdatesCLI : CordaCliWrapper("corda-updates-shell", "Shell for corda-updates CLI utility") {
    companion object {
        private val logger by lazy { loggerFor<CordaCliWrapper>() }
    }

    @CommandLine.Option(names = ["--remoteRepoUrl", "-r"],
            description = ["Remote repository URL."],
            required = true)
    private var remoteRepoUrl : String? = null

    @CommandLine.Option(names = ["--localRepoPath", "-l"],
            description = ["Local repository path."],
            required = true)
    private var localRepoPath : String? = null

    @CommandLine.Option(names = ["--artifact", "-a"],
            description = ["Coordinates of artifact to download in standard Maven notation. Supports version ranges i.e. [0,10.1)."],
            required = true)
    private var artifact : String? = null

    @CommandLine.Option(names = ["--username", "-u"],
            description = ["Username for remote repository."])
    private var password : String? = null

    @CommandLine.Option(names = ["--password", "-p"],
            description = ["Password for remote repository."])
    private var username : String? = null

    @CommandLine.Option(names = ["--proxyUrl", "-pr"],
            description = ["Proxy URL."])
    private var proxyUrl : String? = null

    @CommandLine.Option(names = ["--proxyType", "-pt"],
            description = ["Proxy type. HTTP or HTTPS"],
            defaultValue = "HTTP")
    private var proxyType : String? = null

    @CommandLine.Option(names = ["--proxyPort", "-pp"],
            description = ["Proxy port."])
    private var proxyPort : Int? = null

    @CommandLine.Option(names = ["--proxyUsername", "-pu"],
            description = ["Proxy username."])
    private var proxyUsername : String? = null

    @CommandLine.Option(names = ["--proxyUsername", "-pu"],
            description = ["Proxy username."])
    private var proxyPassword : String? = null

    @CommandLine.Option(names = ["--rpcHost", "-rh"],
            description = ["RPC host. Should be set only if RPC transport is used."])
    private var rpcHost : String? = null

    @CommandLine.Option(names = ["--rpcPort", "-rp"],
            description = ["RPC port. Should be set only if RPC transport is used."])
    private var rpcPort : String? = null

    @CommandLine.Option(names = ["--rpcUsername", "-ru"],
            description = ["RPC username. Should be set only if RPC transport is used."])
    private var rpcUsername : String? = null

    @CommandLine.Option(names = ["--rpcPassword", "-rp"],
            description = ["RPC password. Should be set only if RPC transport is used."])
    private var rpcPassword: String? = null


    override fun runProgram() : Int {
        // setting up authentication
        var authentication : Authentication? = null
        if (username != null && password != null) {
            authentication = AuthenticationBuilder().addUsername(username).addPassword(password).build()
            if (logInfo()) {
                logger.info("Using authentication username=$username password=******")
            }
        }

        // setting up proxy
        var proxy : Proxy? = null
        if (proxyUrl != null && proxyType != null && proxyPort != null) {
            var proxyAuthentication : Authentication? = null
            if (proxyPassword != null && proxyUsername != null) {
                proxyAuthentication = AuthenticationBuilder().addUsername(username).addPassword(password).build()
            }
            proxy = Proxy(proxyType, proxyUrl, proxyPort!!, proxyAuthentication)
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

        resolver.downloadVersionRange(artifact!!, configurationProperties)

        return ExitCodes.SUCCESS
    }

    private fun logInfo() : Boolean = verbose && loggingLevel.toInt() <= Level.INFO.toInt()

}