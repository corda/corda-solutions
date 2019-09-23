package com.r3.businessnetworks.membership.flows.bno.service

import com.r3.businessnetworks.utilities.AbstractConfigurationService
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService

/**
 * Configuration that is used by BNO app. The configuration is red from cordapps/config/membership-service.conf with a fallback to
 * membership-service.conf on the classpath.
 */
@CordaService
class BNOConfigurationService(private val serviceHub: AppServiceHub) : AbstractConfigurationService(serviceHub, "membership-service") {
    override fun bnoName(): CordaX500Name = throw NotImplementedError("This method should not be used")
}