package com.r3.businessnetworks.billing.flows.member.service

import com.r3.businessnetworks.utilities.AbstractConfigurationService
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService

/**
 * Configuration for members
 */
@CordaService
class MemberConfigurationService(appServiceHub : AppServiceHub) : AbstractConfigurationService(appServiceHub, "billing-app") {
    override fun bnoName() : CordaX500Name  = throw NotImplementedError("This method should not be used")
}