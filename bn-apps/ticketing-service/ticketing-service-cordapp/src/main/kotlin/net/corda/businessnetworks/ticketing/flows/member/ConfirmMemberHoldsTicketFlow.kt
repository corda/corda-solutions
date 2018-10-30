package net.corda.businessnetworks.ticketing.flows.member

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.bno.support.BusinessNetworkOperatorInitiatedFlow
import net.corda.businessnetworks.membership.member.support.BusinessNetworkAwareFlowLogic
import net.corda.businessnetworks.membership.states.MembershipState
import net.corda.businessnetworks.ticketing.contracts.Ticket
import net.corda.businessnetworks.ticketing.entity.PartyAndSubject
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.MAX_PAGE_SIZE
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
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
        logger.info("Received $request from ${flowSession.counterparty}")

        progressTracker.currentStep = LOOKNG_FOR_TICKET
        val applicableTickets = findApplicableTickets(request.party, flowSession.counterparty, request.subject)
        logger.info("All applicable tickets: $applicableTickets")

        progressTracker.currentStep = SENDING_RESPONSE_BACK
        flowSession.send(applicableTickets.isNotEmpty())
    }

    private fun findApplicableTickets(holder : Party, applicableTo: Party, subject : Any) : List<Ticket.State<*>> {
        val queryCriteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED)
        val pageSpecification = PageSpecification(1, MAX_PAGE_SIZE)
        val allTickets = serviceHub.vaultService.queryBy<Ticket.State<*>>(queryCriteria, pageSpecification).states.map { it.state.data }
        val applicableTickets = allTickets.filter { it.holder == holder }
                                          .filter { it.subject == subject }
                                          .filter { (it is Ticket.WideTicket) || (it is Ticket.TargetedTicket && it.appliesTo.contains(applicableTo)) }
        return applicableTickets
    }

}

