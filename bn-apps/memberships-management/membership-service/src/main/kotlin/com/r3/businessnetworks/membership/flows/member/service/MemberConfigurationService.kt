package com.r3.businessnetworks.membership.flows.member.service

import com.typesafe.config.ConfigFactory
import com.r3.businessnetworks.membership.flows.ConfigUtils.loadConfig
import com.r3.businessnetworks.membership.states.MembershipContract
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.loggerFor
import java.io.File

/**
 * Configuration that is used by member app.
 */
@CordaService
class MemberConfigurationService(private val serviceHub : ServiceHub) : SingletonSerializeAsToken() {
    companion object {
        // X500 name of the BNO
        const val BNO_WHITELIST = "bnoWhitelist"
        val logger = loggerFor<MemberConfigurationService>()
    }

    private var _config = loadConfig()

    /**
     * BNOs should be explicitly whitelisted. Any attempt to communicate with not whitelisted BNO would fail.
     */
    fun bnoIdentities() : Set<Party> {
        return (if (_config.hasPath(BNO_WHITELIST)) _config.getStringList(BNO_WHITELIST) else listOf())
                .asSequence().map {
                    val x500Name = CordaX500Name.parse(it)
                    val party = serviceHub.identityService.wellKnownPartyFromX500Name(x500Name)
                    if (party == null) {
                        logger.warn("BNO identity $it can't be resolver on the network")
                    }
                    party
                }.filterNotNull().toSet()
    }

    fun reloadConfigurationFromFile(file : File) {
        _config = ConfigFactory.parseFile(file)
    }
}
