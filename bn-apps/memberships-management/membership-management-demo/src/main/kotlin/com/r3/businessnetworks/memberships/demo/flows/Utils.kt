package com.r3.businessnetworks.memberships.demo.flows


import com.r3.businessnetworks.membership.flows.BNONotWhitelisted
import com.r3.businessnetworks.membership.flows.member.service.MemberConfigurationService
import com.r3.businessnetworks.utilities.AbstractConfigurationService
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.AppServiceHub
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService

@CordaService
class CurrentBNOConfigurationService(appServiceHub : AppServiceHub) : AbstractConfigurationService(appServiceHub, "membership-service-current-bno") {
    override fun notaryName() : CordaX500Name = throw NotImplementedError("This method should not be used")
    override fun notaryParty() : Party = throw NotImplementedError("This method should not be used")
}

fun checkIfBNOWhitelisted(bno: Party, serviceHub: ServiceHub) {
    val memberConfigurationService = serviceHub.cordaService(MemberConfigurationService::class.java)
    if (bno !in memberConfigurationService.bnoIdentities())
        throw BNONotWhitelisted(bno)
}

