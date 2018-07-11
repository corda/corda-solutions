package net.corda.businessnetworks.membership.bno

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.bno.service.BNOConfigurationService
import net.corda.businessnetworks.membership.states.Membership
import net.corda.businessnetworks.membership.states.MembershipStatus
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

/**
 * The flow changes status of a PENDING or REVOKED membership to ACTIVE. The flow can be started only by BNO. BNO can unilaterally
 * activate membershipMap and no member's signature is required. After the membership is activated, the flow
 * fires-and-forgets [OnMembershipChanged] notification to the business network members.
 *
 * @param membership membership state to be activated
 */
@InitiatingFlow
class ActivateMembershipFlow(val membership : StateAndRef<Membership.State>) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call() : SignedTransaction {
        // Only BNO should be able to start this flow
        if (ourIdentity != membership.state.data.bno) {
            throw FlowException("Our identity has to be BNO")
        }

        val configuration = serviceHub.cordaService(BNOConfigurationService::class.java)

        // create membership activation transaction
        val notary = configuration.notaryParty()
        val builder = TransactionBuilder(notary)
                .addInputState(membership)
                .addOutputState(membership.state.data.copy(status = MembershipStatus.ACTIVE, modified = serviceHub.clock.instant()), Membership.CONTRACT_NAME)
                .addCommand(Membership.Commands.Activate(), ourIdentity.owningKey)
        builder.verify(serviceHub)
        val selfSignedTx = serviceHub.signInitialTransaction(builder)
        val stx = subFlow(FinalityFlow(selfSignedTx))

        // if notifications are enabled - notify BN about the new joiner
        if (configuration.areNotificationEnabled()) {
            subFlow(NotifyActiveMembersFlow(OnMembershipActivated(membership)))
        }

        return stx
    }
}