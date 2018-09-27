package net.corda.cordaupdates.transport.flows

import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import java.util.*

@CordaService
class BNOConfigurationService(private val serviceHub : AppServiceHub) : SingletonSerializeAsToken() {
    companion object {
        const val PROPERTIES_FILE_NAME = "corda-updates.properties"
        const val REMOTE_REPO_URL = "corda-updates.remoteRepoUrl"
    }
    private var _config = readProps(PROPERTIES_FILE_NAME).toMutableMap()

    fun remoteRepoUrl() : String = _config[REMOTE_REPO_URL]!!

    private fun readProps(fileName : String) : Map<String, String> {
        val input = BNOConfigurationService::class.java.classLoader.getResourceAsStream(fileName)
        val props = Properties()
        props.load(input)
        return props.propertyNames().toList().map { it as String }.map { it to props.getProperty(it)!!}.toMap()
    }
}

object ConfigurationProperties {
    const val APP_SERVICE_HUB = "corda-updates.appServiceHub"
    const val RPC_HOST = "corda-updates.rpcHost"
    const val RPC_PORT = "corda-updates.rpcPort"
    const val RPC_USERNAME = "corda-updates.rpcUsername"
    const val RPC_PASSWORD = "corda-updates.rpcPassword"
}