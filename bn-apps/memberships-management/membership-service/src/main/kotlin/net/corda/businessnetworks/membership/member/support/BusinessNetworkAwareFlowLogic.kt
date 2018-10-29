package net.corda.businessnetworks.membership.member.support

import net.corda.businessnetworks.membership.member.GetNotaryFlow
import net.corda.businessnetworks.membership.member.service.MemberConfigurationService
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party

/**
 * Extend from this class if you are a business network member and you want to make your life easier when writing
 * flows by getting access to the useful methods in this class.
 */
abstract class BusinessNetworkAwareFlowLogic<out T> : FlowLogic<T>() {

    protected fun getBno() : Party {
        val configuration = serviceHub.cordaService(MemberConfigurationService::class.java)
        return configuration.bnoParty()
    }

    protected fun getNotary() : Party {
        return subFlow(GetNotaryFlow())
    }

}