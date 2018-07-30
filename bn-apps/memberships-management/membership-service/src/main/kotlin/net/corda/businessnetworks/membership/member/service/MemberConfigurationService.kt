package net.corda.businessnetworks.membership.member.service

import net.corda.businessnetworks.membership.Utils.readProps
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken

@CordaService
class MemberConfigurationService(private val serviceHub : ServiceHub) : SingletonSerializeAsToken() {
    companion object {
        const val PROPERTIES_FILE_NAME = "membership-service.properties"
        const val BNO_NAME = "net.corda.businessnetworks.membership.bnoName"
    }
    private val _config = readProps(PROPERTIES_FILE_NAME)

    private fun bnoName() = CordaX500Name.parse(_config[BNO_NAME]!!)
    fun bnoParty() = serviceHub.identityService.wellKnownPartyFromX500Name(bnoName())!!
}