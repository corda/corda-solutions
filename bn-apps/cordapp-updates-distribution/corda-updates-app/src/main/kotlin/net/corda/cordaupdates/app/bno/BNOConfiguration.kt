package net.corda.cordaupdates.app.bno

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.core.internal.div
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import java.io.File
import java.nio.file.Paths

@CordaService
class BNOConfiguration(private val appServiceHub : AppServiceHub) : SingletonSerializeAsToken() {
    companion object {
        const val PROPERTIES_FILE_NAME = "corda-updates-app.conf"
        const val SESSION_FILTER = "sessionFilter"
    }
    private var _config = readProps((Paths.get("cordapps") / "config" / PROPERTIES_FILE_NAME).toFile())

    fun reloadConfigurationFromFile(file : File) {
        _config = readProps(file)
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