package net.corda.businessnetworks.membership.member

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.member.support.BusinessNetworkAwareFlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

@StartableByRPC
@InitiatingFlow
class GetNotaryFlow() : BusinessNetworkAwareFlowLogic<Party>() {

    companion object {
        object AWAITING_NOTARY_IDENTITY : ProgressTracker.Step("Awaiting notary identity from BNO")

        fun tracker() = ProgressTracker(
                AWAITING_NOTARY_IDENTITY
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call() : Party {

        progressTracker.currentStep = AWAITING_NOTARY_IDENTITY
        val bno = getBno()
        val bnoSession = initiateFlow(bno)
        return bnoSession.receive<Party>().unwrap {it}

    }

}