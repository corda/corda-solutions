package com.r3.businessnetworks.membership.bno

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.commons.SupportFinalityFlow
import com.r3.businessnetworks.membership.bno.service.BNOConfigurationService
import com.r3.businessnetworks.membership.bno.service.DatabaseService
import com.r3.businessnetworks.membership.bno.support.BusinessNetworkOperatorInitiatedFlow
import com.r3.businessnetworks.membership.member.AmendMembershipMetadataFlow
import com.r3.businessnetworks.membership.member.AmendMembershipMetadataRequest
import com.r3.businessnetworks.membership.states.MembershipContract
import com.r3.businessnetworks.membership.states.MembershipState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap

/**
 * BNO's responder to the [AmendMembershipMetadataFlow]. Receives an [AmendMembershipMetadataRequest], issues an amend membership transaction
 * and notifies all business network members via [OnMembershipChanged] when the transaction is finalised. Only ACTIVE members can
 * amend their metadata.
 */
@InitiatedBy(AmendMembershipMetadataFlow::class)
class AmendMembershipMetadataFlowResponder(flowSession : FlowSession) : BusinessNetworkOperatorInitiatedFlow<Unit>(flowSession) {

    @Suspendable
    override fun onCounterpartyMembershipVerified(counterpartyMembership : StateAndRef<MembershipState<Any>>) {
        // await for a message with a proposed metadata change
        val metadataChangeRequest = flowSession.receive<AmendMembershipMetadataRequest>().unwrap{ it }

        // build transaction
        val configuration = serviceHub.cordaService(BNOConfigurationService::class.java)
        val notaryParty = configuration.notaryParty()

        val newMembership = counterpartyMembership.state.data
                .copy(membershipMetadata = metadataChangeRequest.metadata, modified = serviceHub.clock.instant())

        // changes to the metadata should be governed by the contract, not flows
        val builder = TransactionBuilder(notaryParty)
                .addInputState(counterpartyMembership)
                .addOutputState(newMembership, configuration.membershipContractName())
                .addCommand(MembershipContract.Commands.Amend(), flowSession.counterparty.owningKey, ourIdentity.owningKey)

        builder.verify(serviceHub)

        val selfSignedTx = serviceHub.signInitialTransaction(builder)
        val allSignedTx = subFlow(CollectSignaturesFlow(selfSignedTx, listOf(flowSession)))
        subFlow(SupportFinalityFlow(allSignedTx) {
            listOf(flowSession)
        })

        // notify members about the changes
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        val amendedMembership = databaseService.getMembership(flowSession.counterparty, ourIdentity, configuration.membershipContractName())!!
        subFlow(NotifyActiveMembersFlow(OnMembershipChanged(amendedMembership)))
    }
}
