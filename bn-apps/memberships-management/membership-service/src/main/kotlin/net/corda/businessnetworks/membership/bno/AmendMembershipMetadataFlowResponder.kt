package net.corda.businessnetworks.membership.bno

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.bno.service.BNOConfigurationService
import net.corda.businessnetworks.membership.bno.service.DatabaseService
import net.corda.businessnetworks.membership.bno.support.BusinessNetworkOperatorInitiatedFlow
import net.corda.businessnetworks.membership.member.AmendMembershipMetadataFlow
import net.corda.businessnetworks.membership.member.AmendMembershipMetadataRequest
import net.corda.businessnetworks.membership.states.Membership
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap

@InitiatedBy(AmendMembershipMetadataFlow::class)
class AmendMembershipMetadataFlowResponder(flowSession : FlowSession) : BusinessNetworkOperatorInitiatedFlow<Unit>(flowSession) {

    @Suspendable
    override fun onOtherPartyMembershipVerified() {
        // await for a message with a proposed metadata change
        val metadataChangeRequest = flowSession.receive<AmendMembershipMetadataRequest>().unwrap{ it }

        // build transaction
        val configuration = serviceHub.cordaService(BNOConfigurationService::class.java)
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        val notaryParty = configuration.notaryParty()
        val existingMembership = databaseService.getMembership(flowSession.counterparty)!!

        val newMembership = existingMembership.state.data
                .copy(membershipMetadata = metadataChangeRequest.metadata,
                        modified = serviceHub.clock.instant())

        // changes to the metadata should be governed by the contract, not flows
        val builder = TransactionBuilder(notaryParty)
                .addInputState(existingMembership)
                .addOutputState(newMembership, Membership.CONTRACT_NAME)
                .addCommand(Membership.Commands.Amend(), flowSession.counterparty.owningKey, ourIdentity.owningKey)

        builder.verify(serviceHub)

        val selfSignedTx = serviceHub.signInitialTransaction(builder)
        val allSignedTx = subFlow(CollectSignaturesFlow(selfSignedTx, listOf(flowSession)))
        subFlow(FinalityFlow(allSignedTx, listOf(flowSession)))

        // if notifications are enabled - notify the BN members
        if (configuration.areNotificationEnabled()) {
            val membershipStateAndRef = databaseService.getMembership(flowSession.counterparty)!!
            subFlow(NotifyActiveMembersFlow(OnMembershipChanged(membershipStateAndRef)))
        }
    }
}