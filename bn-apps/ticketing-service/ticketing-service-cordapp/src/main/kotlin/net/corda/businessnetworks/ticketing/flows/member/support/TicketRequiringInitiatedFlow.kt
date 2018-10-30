package net.corda.businessnetworks.ticketing.flows.member.support

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.NotAMemberException
import net.corda.businessnetworks.membership.member.GetMembershipsFlow
import net.corda.businessnetworks.membership.member.support.BusinessNetworkAwareInitiatedFlow
import net.corda.businessnetworks.ticketing.TriggeringThisFlowRequiresTicket
import net.corda.businessnetworks.ticketing.flows.member.ConfirmMemberHoldsTicketFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.Party

/**
 * Extend from this flow if your initiated flow requires a ticket granted by the BNO. Put your flow logic code inside
 * onTicketVerified method. It will be called after other party membership *and* ownership of the required ticket
 * is verified.
 */
abstract class TicketRequiringInitiatedFlow<out T>(flowSession: FlowSession, val requiredTicketSubject : Any) : BusinessNetworkAwareInitiatedFlow<T>(flowSession) {

    @Suspendable
    override fun onOtherPartyMembershipVerified(): T {
        verifyTicketOwnership()
        logger.info("Calling onTicketVerified()")
        return onTicketVerified()
    }

    @Suspendable
    abstract fun onTicketVerified() : T

    @Suspendable
    private fun verifyTicketOwnership() {
        val bno = getBno()
        val counterParty = flowSession.counterparty
        logger.info("Asking BNO $bno whether $counterParty has ticket for $requiredTicketSubject")
        val bnoAnswer = subFlow(ConfirmMemberHoldsTicketFlow(counterParty, requiredTicketSubject))
        logger.info("BNO answered $bnoAnswer")
        if(!bnoAnswer) {
            throw TriggeringThisFlowRequiresTicket(requiredTicketSubject)
        }
    }
}

