package net.corda.businessnetworks.membership.bno

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.bno.service.BNOConfigurationService
import net.corda.businessnetworks.membership.bno.support.BusinessNetworkAwareFlow
import net.corda.businessnetworks.membership.states.Membership
import net.corda.businessnetworks.membership.states.MembershipStatus
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

@InitiatingFlow
class RevokeMembershipFlow(val membership : StateAndRef<Membership.State>) : BusinessNetworkAwareFlow<SignedTransaction>() {

    @Suspendable
    override fun call() : SignedTransaction {
        checkWeAreTheBNOOnThisMembership(membership.state.data)
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