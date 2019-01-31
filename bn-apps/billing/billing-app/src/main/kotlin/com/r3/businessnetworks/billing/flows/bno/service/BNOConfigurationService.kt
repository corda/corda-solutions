package com.r3.businessnetworks.billing.flows.bno.service

import com.r3.businessnetworks.utilities.AbstractConfigurationService
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService

@CordaService
class BNOConfigurationService(appServiceHub : AppServiceHub) : AbstractConfigurationService(appServiceHub, "billing-app")