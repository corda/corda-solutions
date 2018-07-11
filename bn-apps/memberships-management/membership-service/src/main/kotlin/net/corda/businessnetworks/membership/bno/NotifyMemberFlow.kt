package net.corda.businessnetworks.membership.bno

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.bno.service.DatabaseService
import net.corda.businessnetworks.membership.states.Membership
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class OnMembershipActivated(val changedMembership : StateAndRef<Membership.State>)

@CordaSerializable
data class OnMembershipChanged(val changedMembership : StateAndRef<Membership.State>)

@CordaSerializable
data class OnMembershipRevoked(val revokedMember : Party)

class NotifyActiveMembersFlow(private val notification : Any) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        databaseService.getActiveMemberships().forEach { subFlow(NotifyMemberFlow(notification, it.state.data.member)) }
    }
}

@InitiatingFlow
class NotifyMemberFlow(private val notification : Any, private val member : Party) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        initiateFlow(member).send(notification)
    }
}



