package net.corda.businessnetworks.membership.bno

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.bno.service.BNOConfigurationService
import net.corda.businessnetworks.membership.bno.service.DatabaseService
import net.corda.businessnetworks.membership.bno.support.BusinessNetworkOperatorFlowLogic
import net.corda.businessnetworks.membership.states.MembershipContract
import net.corda.businessnetworks.membership.states.MembershipState
import net.corda.businessnetworks.membership.states.MembershipStatus
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

/**
 * A flow that is used by BNO to suspend a membership. BNO can unilaterally suspend memberships, for example as a result of the governance
 * action.
 */
@InitiatingFlow
@StartableByRPC
class SuspendMembershipFlow(val membership : StateAndRef<MembershipState<Any>>) : BusinessNetworkOperatorFlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call() : SignedTransaction {
        verifyThatWeAreBNO(membership.state.data)
        val configuration = serviceHub.cordaService(BNOConfigurationService::class.java)
        val notary = configuration.notaryParty()

        // build suspension transaction
        val builder = TransactionBuilder(notary)
                .addInputState(membership)
                .addOutputState(membership.state.data.copy(status = MembershipStatus.SUSPENDED, modified = serviceHub.clock.instant()), configuration.membershipContractName())
                .addCommand(MembershipContract.Commands.Suspend(), ourIdentity.owningKey)
        builder.verify(serviceHub)
        val selfSignedTx = serviceHub.signInitialTransaction(builder)

        val finalisedTx = subFlow(FinalityFlow(selfSignedTx))

        val dbService = serviceHub.cordaService(DatabaseService::class.java)
        val suspendedMembership = dbService.getMembership(membership.state.data.member, ourIdentity, configuration.membershipContractName())!!

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
class SuspendMembershipForPartyFlow(val party : Party) : BusinessNetworkOperatorFlowLogic<SignedTransaction>() {

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
    override fun call() : SignedTransaction {
        progressTracker.currentStep = LOOKING_FOR_MEMBERSHIP_STATE
        val stateToActivate = findMembershipStateForParty(party)

        progressTracker.currentStep = SUSPENDING_THE_MEMBERSHIP_STATE
        return subFlow(SuspendMembershipFlow(stateToActivate))
    }

}