package net.corda.cordaupdates.app.member

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import java.io.File
import java.nio.file.Paths

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
    private var _config = readProps((Paths.get("cordapps") / "config" / PROPERTIES_FILE_NAME).toFile())

    fun reloadConfigurationFromFile(file : File) {
        _config = readProps(file)
    }

    fun syncerConfig() = getValue(SYNCER_CONFIGURATION_PATH)
    fun syncInterval() = getValue(SYNC_INTERVAL)?.toLong() ?: DEFAULT_SYNC_INTERVAL
    fun notaryParty() = serviceHub.networkMapCache.getNotary(notaryName())!!
    fun bnoParty() = serviceHub.identityService.wellKnownPartyFromX500Name(bnoName())!!

    private fun bnoName() = CordaX500Name.parse(getValue(BNO_NAME)!!)
    private fun notaryName() = CordaX500Name.parse(getValue(NOTARY_NAME)!!)

    private fun readProps(file : File) : Config = ConfigFactory.parseFile(file)

    private fun getValue(key : String)= if (_config.hasPath(key)) _config.getString(key) else null
}

