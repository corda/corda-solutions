package net.corda.businessnetworks.membership.bno

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.bno.BNOUtils.verifyMembership
import net.corda.businessnetworks.membership.bno.service.BNOConfigurationService
import net.corda.businessnetworks.membership.bno.service.DatabaseService
import net.corda.businessnetworks.membership.member.AmendMembershipMetadataFlow
import net.corda.businessnetworks.membership.member.AmendMembershipMetadataRequest
import net.corda.businessnetworks.membership.states.Membership
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap

@InitiatedBy(AmendMembershipMetadataFlow::class)
class AmendMembershipMetadataFlowResponder(val session : FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        // verify that the counterparty's membership is active
        verifyMembership(serviceHub, session.counterparty)

        // await for a message with a proposed metadata change
        val metadataChangeRequest = session.receive<AmendMembershipMetadataRequest>().unwrap{ it }

        // build transaction
        val configuration = serviceHub.cordaService(BNOConfigurationService::class.java)
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        val notaryParty = configuration.notaryParty()
        val existingMembership = databaseService.getMembership(session.counterparty)!!

        val newMembership = existingMembership.state.data
                .copy(membershipMetadata = metadataChangeRequest.metadata,
                        modified = serviceHub.clock.instant())

        // changes to the metadata should be governed by the contract, not flows
        val builder = TransactionBuilder(notaryParty)
                .addInputState(existingMembership)
                .addOutputState(newMembership, Membership.CONTRACT_NAME)
                .addCommand(Membership.Commands.Amend(), session.counterparty.owningKey, ourIdentity.owningKey)

        builder.verify(serviceHub)

        val selfSignedTx = serviceHub.signInitialTransaction(builder)
        val allSignedTx = subFlow(CollectSignaturesFlow(selfSignedTx, listOf(session)))
        subFlow(FinalityFlow(allSignedTx))

        // if notifications are enabled - notify the BN members
        if (configuration.areNotificationEnabled()) {
            val membershipStateAndRef = databaseService.getMembership(session.counterparty)!!
            subFlow(NotifyActiveMembersFlow(OnMembershipChanged(membershipStateAndRef)))
        }
    }
}