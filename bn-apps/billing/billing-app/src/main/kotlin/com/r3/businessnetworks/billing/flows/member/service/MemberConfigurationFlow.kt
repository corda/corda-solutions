package com.r3.businessnetworks.billing.flows.member.service

import com.r3.businessnetworks.utilities.AbstractConfigurationService
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService

@CordaService
class MemberConfigurationService(appServiceHub : AppServiceHub) : AbstractConfigurationService(appServiceHub, "billing-app")