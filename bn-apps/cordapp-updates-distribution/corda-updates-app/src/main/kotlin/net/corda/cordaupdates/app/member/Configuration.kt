package net.corda.cordaupdates.app.member

import net.corda.core.identity.CordaX500Name
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import java.util.*

@CordaService
class MemberConfiguration(private val serviceHub : AppServiceHub) : SingletonSerializeAsToken() {
    companion object {
        const val PROPERTIES_FILE_NAME = "corda-updates.properties"
        const val SYNCER_CONFIGURATION_PATH = "corda-updates.syncerConfig"
        const val SYNC_INTERVAL = "corda-updates.syncInterval"
        const val NOTARY_NAME = "corda-updates.notary"
        const val BNO_NAME = "corda-updates.bno"
    }
    private val config = readProps(PROPERTIES_FILE_NAME).toMap()

    fun syncerConfig() = config[SYNCER_CONFIGURATION_PATH]
    fun syncInterval() = config[SYNC_INTERVAL]?.toLong() ?: 18000L
    fun notaryName() = CordaX500Name.parse(config[NOTARY_NAME]!!)
    fun notaryParty() = serviceHub.networkMapCache.getNotary(notaryName())!!
    fun bnoName() = CordaX500Name.parse(config[BNO_NAME]!!)
    fun bnoParty() = serviceHub.networkMapCache.getNotary(bnoName())!!

    private fun readProps(fileName : String) : Map<String, String> {
        val input = MemberConfiguration::class.java.classLoader.getResourceAsStream(fileName)
        val props = Properties()
        props.load(input)
        return props.propertyNames().toList().map { it as String }.map { it to props.getProperty(it)!!}.toMap()
    }
}

