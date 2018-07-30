package net.corda.businessnetworks.membership.bno

import net.corda.businessnetworks.membership.bno.service.DatabaseService
import net.corda.core.flows.FlowException
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub

object BNOUtils {
    fun verifyMembership(serviceHub : ServiceHub, partyToVerify : Party) {
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        val membership = databaseService.getMembership(partyToVerify)
        if (membership == null) {
            throw FlowException("$partyToVerify is not a member")
        } else if (!membership.state.data.isActive()) {
            throw FlowException("$partyToVerify membership is not active")
        }
    }
}