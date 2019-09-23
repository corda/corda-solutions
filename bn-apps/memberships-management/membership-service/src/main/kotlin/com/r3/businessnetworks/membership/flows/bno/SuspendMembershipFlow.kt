package com.r3.businessnetworks.membership.flows.bno

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.flows.bno.service.BNOConfigurationService
import com.r3.businessnetworks.membership.flows.bno.service.DatabaseService
import com.r3.businessnetworks.membership.flows.bno.support.BusinessNetworkOperatorFlowLogic
import com.r3.businessnetworks.membership.flows.getAttachmentIdForGenericParam
import com.r3.businessnetworks.membership.flows.isAttachmentRequired
import com.r3.businessnetworks.membership.states.MembershipContract
import com.r3.businessnetworks.membership.states.MembershipState
import com.r3.businessnetworks.membership.states.MembershipStatus
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

/**
 * A flow that is used by BNO to suspend a membership. BNO can unilaterally suspend memberships, for example as a result of the governance
 * action.
 */
@StartableByRPC
@InitiatingFlow(version = 2)
open class SuspendMembershipFlow(val membership: StateAndRef<MembershipState<Any>>) : BusinessNetworkOperatorFlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        verifyThatWeAreBNO(membership.state.data)
        val configuration = serviceHub.cordaService(BNOConfigurationService::class.java)
        val notary = configuration.notaryParty()

        // build suspension transaction
        val builder = TransactionBuilder(notary)
                .addInputState(membership)
                .addOutputState(membership.state.data.copy(status = MembershipStatus.SUSPENDED, modified = serviceHub.clock.instant()))
                .addCommand(MembershipContract.Commands.Suspend(), ourIdentity.owningKey)

        if (membership.isAttachmentRequired()) builder.addAttachment(membership.getAttachmentIdForGenericParam())

        builder.verify(serviceHub)
        val selfSignedTx = serviceHub.signInitialTransaction(builder)

        val memberSession = initiateFlow(membership.state.data.member)

        val finalisedTx = if (memberSession.getCounterpartyFlowInfo().flowVersion == 1) {
            subFlow(FinalityFlow(selfSignedTx))
        } else {
            subFlow(FinalityFlow(selfSignedTx, memberSession))
        }

        val dbService = serviceHub.cordaService(DatabaseService::class.java)
        // find member on a specific Network ID
        val suspendedMembership =
                dbService.getMembershipOnNetwork(membership.state.data.member, ourIdentity, membership.state.data.networkID)
                        ?: throw FlowException("Membership for ${membership.state.data.member} has not been found on the network ${membership.state.data.networkID}")

        // notify other members about suspension
        subFlow(NotifyActiveMembersFlow(OnMembershipChanged(suspendedMembership)))
        // sending notification to the suspended member separately
        val suspendedMember = suspendedMembership.state.data.member
        subFlow(NotifyMemberFlow(OnMembershipChanged(suspendedMembership), suspendedMember))

        return finalisedTx
    }
}

/**
 * This is a convenience flow that can be easily used from a command line
 *
 * @param party whose membership state to be suspended
 */
@InitiatingFlow
@StartableByRPC
open class SuspendMembershipForPartyFlow(val party: Party, val networkID: String?) : BusinessNetworkOperatorFlowLogic<SignedTransaction>() {

    companion object {
        object LOOKING_FOR_MEMBERSHIP_STATE : ProgressTracker.Step("Looking for party's membership state")
        object SUSPENDING_THE_MEMBERSHIP_STATE : ProgressTracker.Step("Suspending the membership state")

        fun tracker() = ProgressTracker(
                LOOKING_FOR_MEMBERSHIP_STATE,
                SUSPENDING_THE_MEMBERSHIP_STATE
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = LOOKING_FOR_MEMBERSHIP_STATE
        val stateToActivate = findMembershipStateForParty(party, networkID)

        progressTracker.currentStep = SUSPENDING_THE_MEMBERSHIP_STATE
        return subFlow(SuspendMembershipFlow(stateToActivate))
    }

}