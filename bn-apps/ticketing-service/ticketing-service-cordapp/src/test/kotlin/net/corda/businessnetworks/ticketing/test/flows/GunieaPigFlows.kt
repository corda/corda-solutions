package net.corda.businessnetworks.ticketing.test.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.member.support.BusinessNetworkAwareFlowLogic
import net.corda.businessnetworks.ticketing.flows.member.support.TicketRequiringInitiatedFlow
import net.corda.businessnetworks.ticketing.test.TicketingServiceTestsSupport
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.utilities.unwrap

@InitiatingFlow
class TestInitiator(val counterParty : Party) : BusinessNetworkAwareFlowLogic<String>() {

    @Suspendable
    override fun call() : String {
        val flowSession = initiateFlow(counterParty)
        val returnedGreeting = flowSession.sendAndReceive<String>("Hello").unwrap { it }
        return returnedGreeting
    }
}

@InitiatedBy(TestInitiator::class)
class TestResponder(flowSession : FlowSession) : TicketRequiringInitiatedFlow<Unit>(flowSession, TicketingServiceTestsSupport.TestTicketSubject.SUBJECT_1) {

    @Suspendable
    override fun onTicketVerified() {
        flowSession.receive<String>().unwrap {it}
        flowSession.send("Hi")
    }

}