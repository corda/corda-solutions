package net.corda.businessnetworks.membership.bno.service

import net.corda.businessnetworks.membership.Utils.readProps
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken

@CordaService
class BNOConfigurationService(private val serviceHub : ServiceHub) : SingletonSerializeAsToken() {
    companion object {
        const val PROPERTIES_FILE_NAME = "membership-service.properties"
        const val NOTARY_NAME = "net.corda.businessnetworks.membership.notaryName"
        // Specifies how often BN members should be refreshing their membership list caches. If this attribute is not set, then
        // the BN members will pull membership list only once, when their node starts, and then would rely on the BNO to notify them
        // about any membership change. Notifications can be enabled via NOTIFICATIONS_ENABLED flag
        const val CACHE_REFRESH_PERIOD = "net.corda.businessnetworks.membership.cacheRefreshPeriod"
        // Specifies whether Notifications are enabled. Should be set to true if CACHE_REFRESH_PERIOD is not specified.
        const val NOTIFICATIONS_ENABLED = "net.corda.businessnetworks.membership.notificationsEnabled"
    }
    private var _config = readProps(PROPERTIES_FILE_NAME)

    private fun notaryName() : CordaX500Name = CordaX500Name.parse(_config[NOTARY_NAME]!!)
    fun notaryParty() = serviceHub.networkMapCache.getNotary(notaryName())!!
    fun cacheRefreshPeriod() = _config[CACHE_REFRESH_PERIOD]?.toInt()
    fun areNotificationEnabled() = _config[NOTIFICATIONS_ENABLED]?.toBoolean() ?: false

    fun reloadPropertiesFromFile(fileName : String) {
        _config = readProps(fileName)
    }
 }