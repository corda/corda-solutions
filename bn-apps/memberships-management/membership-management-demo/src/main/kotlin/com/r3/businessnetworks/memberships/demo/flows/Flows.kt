package com.r3.businessnetworks.memberships.demo.flows


import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.flows.BNONotWhitelisted
import com.r3.businessnetworks.membership.flows.member.GetMembershipsFlow
import com.r3.businessnetworks.membership.flows.member.service.MemberConfigurationService
import com.r3.businessnetworks.membership.states.MembershipState
import com.r3.businessnetworks.memberships.demo.contracts.AssetContract
import com.r3.businessnetworks.memberships.demo.contracts.AssetState
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap

@CordaSerializable
class RequestMembershipForTransaction(val bno: Party)

fun checkIfBNOWhitelisted (bno : Party, serviceHub : ServiceHub) {
    val memberConfigurationService = serviceHub.cordaService(MemberConfigurationService::class.java)
    if (bno !in memberConfigurationService.bnoIdentities())
        throw BNONotWhitelisted(bno)
}

class IssueAssetFlow(val bno: Party) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // check if bno is whitelisted
        checkIfBNOWhitelisted (bno, serviceHub)

        // for the sake of demo we are just taking the first available notary
        val notary = serviceHub.networkMapCache.notaryIdentities.single()

        // get the node's membership (from the node's membership cache or from the bno)
        val ourMembership = subFlow(GetMembershipsFlow(bno))[ourIdentity]
                ?: throw FlowException("Membership for $ourIdentity has not been found")

        val builder = TransactionBuilder(notary)
                .addOutputState(AssetState(ourIdentity), AssetContract.CONTRACT_NAME)
                .addCommand(AssetContract.Commands.Issue(), ourIdentity.owningKey)
                .addReferenceState(ReferencedStateAndRef(ourMembership))

        builder.verify(serviceHub)
        val stx = serviceHub.signInitialTransaction(builder)
        return subFlow(FinalityFlow(stx, listOf()))
    }
}

@InitiatingFlow
class TransferAssetFlow(val assetState: StateAndRef<AssetState>, val bno: Party, val partyToTransferTo: Party) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        if (ourIdentity != assetState.state.data.owner)
            throw FlowException("Only owner of the state can transfer it")
        if (ourIdentity == partyToTransferTo)
            throw FlowException("sender and recipient should be different parties")

        // check if bno is whitelisted
        checkIfBNOWhitelisted (bno, serviceHub)

        // for the sake of demo we are just taking the first available notary
        val notary = serviceHub.networkMapCache.notaryIdentities.single()

        // get the node's membership (from the node's membership cache or from the bno)
        val ourMembership = subFlow(GetMembershipsFlow(bno))[ourIdentity]
                ?: throw FlowException("Membership for $ourIdentity has not been found")

        val builder = TransactionBuilder(notary)
                .addReferenceState(ReferencedStateAndRef(ourMembership))
                .addInputState(assetState)
                .addOutputState(AssetState(partyToTransferTo))
                .addCommand(AssetContract.Commands.Transfer(), ourIdentity.owningKey, partyToTransferTo.owningKey)

        // create a session
        val counterPartySession = initiateFlow(partyToTransferTo)
        // requesting other party to send us their membership state
        counterPartySession.send(RequestMembershipForTransaction(bno))
        val counterPartyMembershipState =
                subFlow(ReceiveStateAndRefFlow<MembershipState<Any>>(counterPartySession)).single()

        builder.addReferenceState(ReferencedStateAndRef(counterPartyMembershipState))

        // verifying the transaction
        builder.verify(serviceHub)

        val selfSignedTx = serviceHub.signInitialTransaction(builder)
        val allSignedTx = subFlow(CollectSignaturesFlow(selfSignedTx, listOf(counterPartySession)))

        // notarise the transaction
        return subFlow(FinalityFlow(allSignedTx, listOf(counterPartySession)))
    }
}

@InitiatedBy(TransferAssetFlow::class)
class TransferAssetFlowResponder(val session: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {

        // receiving request for membership state
        val bno = session.receive<RequestMembershipForTransaction>().unwrap { it }.bno

        // check if bno is whitelisted
        checkIfBNOWhitelisted (bno, serviceHub)

        val ourMembership = subFlow(GetMembershipsFlow(bno))[ourIdentity]
                ?: throw FlowException("Membership for $ourIdentity has not been found")

        // sending the states to the requester
        subFlow(SendStateAndRefFlow(session, listOf(ourMembership)))

        val stx = subFlow(object : SignTransactionFlow(session) {
            override fun checkTransaction(stx: SignedTransaction) {
                stx.toLedgerTransaction(serviceHub, false).verify()
            }
        })

        subFlow(ReceiveFinalityFlow(session, stx.id))
    }
}