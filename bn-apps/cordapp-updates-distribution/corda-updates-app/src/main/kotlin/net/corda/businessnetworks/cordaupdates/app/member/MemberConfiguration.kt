package net.corda.businessnetworks.cordaupdates.app.member

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import java.io.File
import java.lang.IllegalArgumentException
import java.nio.file.Paths

/**
 * Member-side cordapp configuration. Configuration is read from "cordapps/config/corda-updates-app.conf" file in the node's folder.
 *
 * TODO: update to use serviceHub.getAppContext().config once it is available
 */
@CordaService
class MemberConfiguration(private val serviceHub : AppServiceHub) : SingletonSerializeAsToken() {

    companion object {
        const val PROPERTIES_FILE_NAME = "corda-updates-app.conf"
        const val SYNCER_CONFIGURATION_PATH = "configPath"
        const val SYNC_INTERVAL = "syncInterval"
        const val NOTARY_NAME = "notary"
        const val BNO_NAME = "bno"
        const val DEFAULT_SYNC_INTERVAL = 18000000L
    }

    private var _config = readProps((Paths.get("cordapps").resolve( "config").resolve(PROPERTIES_FILE_NAME)).toFile())

    val config : Config
        get() = _config

    fun reloadConfigurationFromFile(file : File) {
        _config = readProps(file)
    }

    /**
     * Path to the settings.conf file
     */
    fun syncerConfig() = if (_config.hasPath(SYNCER_CONFIGURATION_PATH)) _config.getString(SYNCER_CONFIGURATION_PATH) else null

    /**
     * Synchronisation interval. Defaults to [DEFAULT_SYNC_INTERVAL] if not specified
     */
    fun syncInterval() = if (_config.hasPath(SYNC_INTERVAL)) _config.getLong(SYNC_INTERVAL) else DEFAULT_SYNC_INTERVAL

    fun notaryParty() =
        serviceHub.networkMapCache.getNotary(notaryName()) ?: throw IllegalArgumentException("Notary ${notaryName()} can't be found on the network")

    fun bnoParty() =
        serviceHub.identityService.wellKnownPartyFromX500Name(bnoName()) ?: throw IllegalArgumentException("BNO ${bnoName()} can't be found on the network")

    private fun bnoName() = CordaX500Name.parse(_config.getString(BNO_NAME))

    private fun notaryName() = CordaX500Name.parse(_config.getString(NOTARY_NAME)!!)

    private fun readProps(file : File) : Config = ConfigFactory.parseFile(file)
}

