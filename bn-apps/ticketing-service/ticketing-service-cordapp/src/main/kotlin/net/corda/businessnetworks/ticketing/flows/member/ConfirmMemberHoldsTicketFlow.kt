package net.corda.businessnetworks.ticketing.flows.member

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.bno.support.BusinessNetworkOperatorInitiatedFlow
import net.corda.businessnetworks.membership.member.support.BusinessNetworkAwareFlowLogic
import net.corda.businessnetworks.membership.states.MembershipState
import net.corda.businessnetworks.ticketing.entity.PartyAndSubject
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

@StartableByRPC
@InitiatingFlow
class ConfirmMemberHoldsTicketFlow<T>(val member : Party, val subject : T) : BusinessNetworkAwareFlowLogic<Boolean>() {

    companion object {
        object ASKING_BNO : ProgressTracker.Step("Asking BNO about ticket")


        fun tracker() = ProgressTracker(
                ASKING_BNO
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): Boolean {
        progressTracker.currentStep = ASKING_BNO
        val bno = getBno()
        val bnoFlowSession = initiateFlow(bno)
        return bnoFlowSession.sendAndReceive<Boolean>(PartyAndSubject(member, subject)).unwrap { it }
    }
}

@InitiatedBy(ConfirmMemberHoldsTicketFlow::class)
class ConfirmMemberHoldsTicketFlowResponder(flowSession : FlowSession) : BusinessNetworkOperatorInitiatedFlow<Unit>(flowSession) {

    companion object {
        object RECEIVING_REQUEST : ProgressTracker.Step("Receiving request")
        object LOOKNG_FOR_TICKET: ProgressTracker.Step("Looking for ticket")
        object SENDING_RESPONSE_BACK: ProgressTracker.Step("Sending response back")

        fun tracker() = ProgressTracker(
                RECEIVING_REQUEST,
                LOOKNG_FOR_TICKET,
                SENDING_RESPONSE_BACK
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun onCounterpartyMembershipVerified(counterpartyMembership: StateAndRef<MembershipState<Any>>) {
        progressTracker.currentStep = RECEIVING_REQUEST
        val request = flowSession.receive<PartyAndSubject<Any>>().unwrap { it }

        progressTracker.currentStep = LOOKNG_FOR_TICKET

        progressTracker.currentStep = SENDING_RESPONSE_BACK
        flowSession.send(false)
    }

}

