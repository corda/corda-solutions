package net.corda.businessnetworks.membership.bno.support

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.bno.service.DatabaseService
import net.corda.businessnetworks.membership.states.Membership
import net.corda.core.contracts.StateAndRef

abstract class BusinessNetworkAwareInitiatingFlow<out T>() : BusinessNetworkAwareFlow<T>() {

    @Suspendable
    override fun call(): T {
        val membershipList = getActiveMembershipStates()
        return callWithActiveMembershipList(membershipList)
    }

    @Suspendable
    abstract fun callWithActiveMembershipList(memberships : List<StateAndRef<Membership.State>>) : T

    protected fun getActiveMembershipStates() : List<StateAndRef<Membership.State>> {
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        return databaseService.getActiveMemberships()
    }


}