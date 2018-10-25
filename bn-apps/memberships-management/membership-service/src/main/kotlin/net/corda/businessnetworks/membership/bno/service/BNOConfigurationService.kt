package net.corda.businessnetworks.membership.bno.service

import com.typesafe.config.ConfigFactory
import net.corda.businessnetworks.membership.ConfigUtils.loadConfig
import net.corda.businessnetworks.membership.bno.extension.MembershipAutoAcceptor
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import java.io.File

@CordaService
class BNOConfigurationService(private val serviceHub : ServiceHub) : SingletonSerializeAsToken() {
    companion object {
        const val NOTARY_NAME = "notaryName"
        // Specifies how often BN members should be refreshing their membership list caches. If this attribute is not set, then
        // the BN members will pull membership list only once, when their node starts, and then would rely on the BNO to notify them
        // about any membership change. Notifications can be enabled via NOTIFICATIONS_ENABLED flag
        const val CACHE_REFRESH_PERIOD = "cacheRefreshPeriod"
        // Specifies whether Notifications are enabled. Should be set to true if CACHE_REFRESH_PERIOD is not specified.
        const val NOTIFICATIONS_ENABLED = "notificationsEnabled"
        // Specifies the class for delegating BNO decisions to
        const val MEMBERSHIP_AUTO_ACCEPTOR = "membershipAutoAcceptor"
    }

    private var _config = loadConfig()

    private fun notaryName() : CordaX500Name = CordaX500Name.parse(_config.getString(NOTARY_NAME))
    fun notaryParty() = serviceHub.networkMapCache.getNotary(notaryName())
            ?: throw IllegalArgumentException("Notary ${notaryName()} has not been found on the network")

    fun cacheRefreshPeriod() = if (_config.hasPath(CACHE_REFRESH_PERIOD)) _config.getLong(CACHE_REFRESH_PERIOD) else null
    fun areNotificationEnabled() = if (_config.hasPath(NOTIFICATIONS_ENABLED)) _config.getBoolean(NOTIFICATIONS_ENABLED) else false

    fun getMembershipAutoAcceptor() : MembershipAutoAcceptor? {
        return if (_config.hasPath(MEMBERSHIP_AUTO_ACCEPTOR)) {
            val className = _config.getString(MEMBERSHIP_AUTO_ACCEPTOR)
            val clazz = Class.forName(className)
            clazz.newInstance() as MembershipAutoAcceptor
        } else null
    }

    fun reloadPropertiesFromFile(file : File) {
        _config = ConfigFactory.parseFile(file)
    }
}