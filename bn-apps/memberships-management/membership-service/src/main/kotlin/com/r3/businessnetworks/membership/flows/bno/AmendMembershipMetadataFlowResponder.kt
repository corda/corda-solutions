package com.r3.businessnetworks.membership.flows.bno

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.flows.bno.service.BNOConfigurationService
import com.r3.businessnetworks.membership.flows.bno.service.DatabaseService
import com.r3.businessnetworks.membership.flows.bno.support.BusinessNetworkOperatorInitiatedFlow
import com.r3.businessnetworks.membership.flows.getAttachmentIdForGenericParam
import com.r3.businessnetworks.membership.flows.isAttachmentRequired
import com.r3.businessnetworks.membership.flows.member.AmendMembershipMetadataFlow
import com.r3.businessnetworks.membership.flows.member.AmendMembershipMetadataRequest
import com.r3.businessnetworks.membership.states.MembershipContract
import com.r3.businessnetworks.membership.states.MembershipState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
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
open class AmendMembershipMetadataFlowResponder(flowSession: FlowSession) : BusinessNetworkOperatorInitiatedFlow<Unit>(flowSession) {

    @Suspendable
    override fun onCounterpartyMembershipVerified(counterpartyMembership: StateAndRef<MembershipState<Any>>) {
        // await for a message with a proposed metadata change
<<<<<<< HEAD
        val metadataChangeRequest = flowSession.receive<AmendMembershipMetadataRequest>().unwrap { it }
=======

        val metadataChangeRequest = flowSession.receive<AmendMembershipMetadataRequest>().unwrap { it }
        val x :String = "0"
>>>>>>> 65dbaa9b6f68c32f9b8bd5ac77fa93cb3d035ec0
        // build transaction
        val configuration = serviceHub.cordaService(BNOConfigurationService::class.java)
        val notaryParty = configuration.notaryParty()

        val newMembership = counterpartyMembership.state.data
            .copy(membershipMetadata = metadataChangeRequest.metadata, modified = serviceHub.clock.instant())

        // changes to the metadata should be governed by the contract, not flows
        val builder = TransactionBuilder(notaryParty)
            .addInputState(counterpartyMembership)
            .addOutputState(newMembership, MembershipContract.CONTRACT_NAME)
            .addCommand(MembershipContract.Commands.Amend(), flowSession.counterparty.owningKey, ourIdentity.owningKey)

        if (counterpartyMembership.isAttachmentRequired())
            builder.addAttachment(counterpartyMembership.getAttachmentIdForGenericParam())

        verifyTransaction(builder)

        val selfSignedTx = serviceHub.signInitialTransaction(builder)
        val allSignedTx = subFlow(CollectSignaturesFlow(selfSignedTx, listOf(flowSession)))

        if (flowSession.getCounterpartyFlowInfo().flowVersion == 1) {
            subFlow(FinalityFlow(allSignedTx))
        } else {
            subFlow(FinalityFlow(allSignedTx, listOf(flowSession)))
        }

        // notify members about the changes
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        val amendedMembership = databaseService.getMembershipOnNetwork(flowSession.counterparty, ourIdentity, metadataChangeRequest.networkID)!!
        subFlow(NotifyActiveMembersFlow(OnMembershipChanged(amendedMembership)))
    }

    /**
     * This method can be overridden to add custom verification membership metadata verifications.
     * See: https://docs.corda.net/head/flow-overriding.html
     */
    @Suspendable
    protected open fun verifyTransaction(builder: TransactionBuilder) {
        builder.verify(serviceHub)
    }
}
