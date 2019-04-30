package com.r3.businessnetworks.membership.flows.member.service

import com.r3.businessnetworks.utilities.AbstractConfigurationService
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.utilities.loggerFor

/**
 * Configuration that is used by member app.
 */
@CordaService
class MemberConfigurationService(private val serviceHub : AppServiceHub) : AbstractConfigurationService(serviceHub, "membership-service") {
    companion object {
        // X500 name of the BNO
        const val BNO_WHITELIST = "bnoWhitelist"
        val logger = loggerFor<MemberConfigurationService>()
    }

    override fun bnoName() : CordaX500Name  = throw NotImplementedError("This method should not be used")
    override fun notaryName() = throw NotImplementedError("This method should not be used")

    /**
     * BNOs should be explicitly whitelisted. Any attempt to communicate with not whitelisted BNO would fail.
     */
    fun bnoIdentities() : Set<Party> {
        val config = _config ?: throw IllegalArgumentException("Configuration for membership service is missing")
        return (if (config.hasPath(BNO_WHITELIST)) config.getStringList(BNO_WHITELIST) else listOf())
                .asSequence().map {
                    val x500Name = CordaX500Name.parse(it)
                    val party = serviceHub.identityService.wellKnownPartyFromX500Name(x500Name)
                    if (party == null) {
                        logger.warn("BNO identity $it can't be resolver on the network")
                    }
                    party
                }.filterNotNull().toSet()
    }
}
