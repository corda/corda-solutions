package net.corda.businessnetworks.membership.member.service

import net.corda.businessnetworks.membership.ConfigUtils.loadConfig
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken

@CordaService
class MemberConfigurationService(private val serviceHub : ServiceHub) : SingletonSerializeAsToken() {
    companion object {
        const val BNO_NAME = "bnoName"
    }
    private val _config = loadConfig()

    private fun bnoName() = CordaX500Name.parse(_config.getString(BNO_NAME))
    fun bnoParty() = serviceHub.identityService.wellKnownPartyFromX500Name(bnoName()) ?: throw IllegalArgumentException("Party ${bnoName()} was not found on the network")
}