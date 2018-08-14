package net.corda.businessnetworks.membership.bno

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.bno.service.BNOConfigurationService
import net.corda.businessnetworks.membership.bno.support.BusinessNetworkOperatorSupportFlow
import net.corda.businessnetworks.membership.states.Membership
import net.corda.businessnetworks.membership.states.MembershipStatus
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

@InitiatingFlow
class RevokeMembershipFlow(val membership : StateAndRef<Membership.State>) : BusinessNetworkOperatorSupportFlow<SignedTransaction>() {

    @Suspendable
    override fun call() : SignedTransaction {
        verifyThatWeAreBNO(membership.state.data)
        val configuration = serviceHub.cordaService(BNOConfigurationService::class.java)
        val notary = configuration.notaryParty()

        // build revocation transaction
        val builder = TransactionBuilder(notary)
                .addInputState(membership)
                .addOutputState(membership.state.data.copy(status = MembershipStatus.REVOKED, modified = serviceHub.clock.instant()), Membership.CONTRACT_NAME)
                .addCommand(Membership.Commands.Revoke(), ourIdentity.owningKey)
        builder.verify(serviceHub)
        val selfSignedTx = serviceHub.signInitialTransaction(builder)
        val finalisedTx = subFlow(FinalityFlow(selfSignedTx))

        // notify other members about revocation
        if (configuration.areNotificationEnabled()) {
            subFlow(NotifyActiveMembersFlow(OnMembershipRevoked(membership.state.data.member)))
            // sending notification to the revoked member separately
            val revokedMember = membership.state.data.member
            subFlow(NotifyMemberFlow(OnMembershipRevoked(revokedMember), revokedMember))
        }

        return finalisedTx
    }
}

/**
 * This is a convenience flow that can be easily used from command line
 *
 * @param party whose membership state to be revoked
 */
@InitiatingFlow
@StartableByRPC
class RevokeMembershipForPartyFlow(val party : Party) : BusinessNetworkOperatorSupportFlow<SignedTransaction>() {

    companion object {
        object LOOKING_FOR_MEMBERSHIP_STATE : ProgressTracker.Step("Looking for party's membership state")
        object REVOKING_THE_MEMBERSHIP_STATE : ProgressTracker.Step("Revoking the membership state")

        fun tracker() = ProgressTracker(
                LOOKING_FOR_MEMBERSHIP_STATE,
                REVOKING_THE_MEMBERSHIP_STATE
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call() : SignedTransaction {
        progressTracker.currentStep = LOOKING_FOR_MEMBERSHIP_STATE
        val stateToActivate = findMembershipStateForParty(party)

        progressTracker.currentStep = REVOKING_THE_MEMBERSHIP_STATE
        return subFlow(RevokeMembershipFlow(stateToActivate))
    }

}