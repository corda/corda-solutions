package net.corda.businessnetworks.membership

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.bno.NotifyMemberFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.identity.Party
import net.corda.core.utilities.unwrap

@InitiatedBy(NotifyMemberFlow::class)
class TestNotifyMembersFlowResponder(val session : FlowSession) : FlowLogic<Unit>() {
    companion object {
        val NOTIFICATIONS = mutableListOf<Pair<Party, Any>>()
    }

    @Suspendable
    override fun call() {
        val notification  = session.receive<Any>().unwrap { it }
        NOTIFICATIONS.add(Pair(ourIdentity, notification))
    }
}
