package net.corda.cordaupdates.transport.flows

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.core.internal.div
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import java.io.File
import java.nio.file.Paths

/**
 * Configuration for the party that hosts Maven repo.
 *
 * TODO: replace with serviceHub.getAppContext().config when released
 */
@CordaService
class RepositoryHosterConfigurationService(private val serviceHub : AppServiceHub) : SingletonSerializeAsToken() {
    companion object {
        const val PROPERTIES_FILE_NAME = "corda-updates.properties"
        const val REMOTE_REPO_URL = "remoteRepoUrl"
        const val SESSION_FILTER = "sessionFilter"
    }
    private var _config = readProps((Paths.get("cordapps") / "config" / PROPERTIES_FILE_NAME).toFile())

    fun reloadConfigurationFromFile(file : File) {
        _config = readProps(file)
    }

    fun remoteRepoUrl() : String = getValue(REMOTE_REPO_URL)!!

    fun getSessionFilter() : SessionFilter? {
        val className = getValue(SESSION_FILTER)
        return if (className == null) {
            null
        } else {
            val clazz = Class.forName(className)
            clazz.newInstance() as SessionFilter
        }
    }

    private fun readProps(file : File) : Config = ConfigFactory.parseFile(file)

    private fun getValue(key : String)= if (_config.hasPath(key)) _config.getString(key) else null
}

