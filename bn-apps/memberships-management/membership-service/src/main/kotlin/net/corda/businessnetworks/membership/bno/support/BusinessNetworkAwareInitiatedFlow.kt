package net.corda.businessnetworks.membership.bno.support

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.bno.service.DatabaseService
import net.corda.businessnetworks.membership.common.CounterPartyMembershipNotActive
import net.corda.businessnetworks.membership.common.CounterPartyNotAMemberException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.Party

abstract class BusinessNetworkAwareInitiatedFlow<out T>(val flowSession: FlowSession) : FlowLogic<T>() {

    @Suspendable
    override fun call(): T {
        confirmInitiatorIsAMemberOfOurBN(flowSession.counterparty)
        return afterOtherPartyMembershipChecked()
    }

    @Suspendable
    abstract fun afterOtherPartyMembershipChecked() : T

    @Suspendable
    private fun confirmInitiatorIsAMemberOfOurBN(initiator : Party) {
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        val membership = databaseService.getMembership(initiator)
        if (membership == null) {
            throw CounterPartyNotAMemberException(initiator)
        } else if (!membership.state.data.isActive()) {
            throw CounterPartyMembershipNotActive(initiator)
        }
    }
}

