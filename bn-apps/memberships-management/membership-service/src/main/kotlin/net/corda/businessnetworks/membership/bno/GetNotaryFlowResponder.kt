package net.corda.businessnetworks.membership.bno

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.bno.support.BusinessNetworkOperatorInitiatedFlow
import net.corda.businessnetworks.membership.member.GetNotaryFlow
import net.corda.businessnetworks.membership.states.MembershipState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.utilities.ProgressTracker

@InitiatedBy(GetNotaryFlow::class)
class GetNotaryFlowResponder(flowSession : FlowSession) : BusinessNetworkOperatorInitiatedFlow<Unit>(flowSession) {

    companion object {
        object RETURNING_NOTARY_TO_MEMBER : ProgressTracker.Step("Returning notary to member")

        fun tracker() = ProgressTracker(
                RETURNING_NOTARY_TO_MEMBER
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun onCounterpartyMembershipVerified(counterpartyMembership: StateAndRef<MembershipState<Any>>) {
        progressTracker.currentStep = RETURNING_NOTARY_TO_MEMBER
        flowSession.send(getNotary())
    }

}