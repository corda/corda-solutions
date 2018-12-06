package net.corda.cordaupdates.transport.flows

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.core.flows.FlowException
import net.corda.core.internal.div
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import java.io.File
import java.nio.file.Paths

/**
 * Configuration for the repository hoster.
 *
 * TODO: replace with serviceHub.getAppContext().config when released
 */
@CordaService
class RepositoryHosterConfigurationService(private val serviceHub : AppServiceHub) : SingletonSerializeAsToken() {
    companion object {
        const val PROPERTIES_FILE_NAME = "corda-updates-app.conf"
        const val REPOSITORIES = "repositories"
        const val SESSION_FILTER = "sessionFilter"
    }
    private var _config = readProps((Paths.get("cordapps") / "config" / PROPERTIES_FILE_NAME).toFile())

    fun reloadConfigurationFromFile(file : File) {
        _config = readProps(file)
    }

    /**
     * URL of the remote repository to fetch an artifact from. Supports -http and -file based transports
     */
    fun repositoryUrl(repoName : String) : String {
        // throwing an IllegalArgumentException here because the configuration has to contain "repositories" section.
        // If it doesn't contain - that would mean that the node has been configured incorrectly (akin to 5xx HTTP error)
        if (!_config.hasPath(REPOSITORIES)) {
            throw IllegalArgumentException("$REPOSITORIES attribute is missing from CorDapp config")
        }
        val repoConfig = _config.getConfig(REPOSITORIES)!!
        // throwing FlowException here to let the counterparty know that the repository name they provided is invalid (akin to 4xx HTTP error)
        if (!repoConfig.hasPath(repoName)) {
            throw FlowException("Repository $repoName does not exist")
        }
        return repoConfig.getString(repoName)!!
    }

    /**
     * Returns a session filter to filter out an unauthorised traffic
     */
    fun getSessionFilter() : SessionFilter? {
        val className = if (_config.hasPath(SESSION_FILTER)) _config.getString(SESSION_FILTER) else null
        return if (className == null) {
            null
        } else {
            val clazz = Class.forName(className)
            clazz.newInstance() as SessionFilter
        }
    }

    private fun readProps(file : File) : Config = ConfigFactory.parseFile(file)
}

