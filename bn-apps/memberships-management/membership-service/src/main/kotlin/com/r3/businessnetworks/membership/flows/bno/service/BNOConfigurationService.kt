package com.r3.businessnetworks.membership.flows.bno.service

import com.r3.businessnetworks.membership.flows.ConfigUtils.loadConfig
import com.typesafe.config.ConfigFactory
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import java.io.File

/**
 * Configuration that is used by BNO app. The configuration is red from cordapps/config/membership-service.conf with a fallback to
 * membership-service.conf on the classpath.
 */
@CordaService
class BNOConfigurationService(private val serviceHub : ServiceHub) : SingletonSerializeAsToken() {
    companion object {
        const val NOTARY_NAME = "notaryName"
    }

    private var _config = loadConfig()

    private fun notaryName() : CordaX500Name = CordaX500Name.parse(_config.getString(NOTARY_NAME))
    fun notaryParty() = serviceHub.networkMapCache.getNotary(notaryName())
            ?: throw IllegalArgumentException("Notary ${notaryName()} has not been found on the network")

    fun reloadConfigurationFromFile(file : File) {
        _config = ConfigFactory.parseFile(file)
    }
}