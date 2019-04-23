package com.r3.businessnetworks.memberships.demo.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.flows.bno.ActivateMembershipFlow
import com.r3.businessnetworks.membership.flows.bno.service.DatabaseService
import com.r3.businessnetworks.membership.flows.member.GetMembershipsFlow
import com.r3.businessnetworks.membership.flows.member.MembershipListRequest
import com.r3.businessnetworks.membership.flows.member.MembershipsListResponse
import com.r3.businessnetworks.membership.flows.member.RequestMembershipFlow
import com.r3.businessnetworks.membership.flows.member.service.MembershipsCacheHolder
import com.r3.businessnetworks.membership.states.MembershipContract
import com.r3.businessnetworks.membership.states.MembershipState


import com.r3.businessnetworks.memberships.demo.contracts.SampleContract
import com.r3.businessnetworks.memberships.demo.contracts.SampleState
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap

@CordaSerializable
class RequestMembershipForTransaction (val bno : Party)

class IssueAssetFlow (val bno : Party) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call() : SignedTransaction {
        // for the sake of demo we are just taking the first available notary
        val notary = serviceHub.networkMapCache.notaryIdentities.single()

        // query the vault with bno
        val memberships= subFlow(GetMembershipsFlow(bno))
        val ourMembership = memberships[ourIdentity]!!

        val builder = TransactionBuilder(notary)
                .addOutputState(SampleState(ourIdentity), SampleContract.CONTRACT_NAME)
                .addCommand(SampleContract.Commands.Issue(), ourIdentity.owningKey)
                .addReferenceState(ReferencedStateAndRef(ourMembership))

        builder.verify(serviceHub)
        val stx = serviceHub.signInitialTransaction(builder)
        return subFlow(FinalityFlow(stx, listOf()))
    }
}

@InitiatingFlow
class TransferAssetFlow(val sampleState : StateAndRef<SampleState>,
                              val bno : Party,
                              val partyToTransferTo : Party) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call() : SignedTransaction {
        if (ourIdentity != sampleState.state.data.owner)
            throw FlowException("Only owner of the state can transfer it")
        if (ourIdentity == partyToTransferTo)
            throw FlowException("sender and recipient should be different parties")
        // for the sake of demo we are just taking the first available notary
        val notary = serviceHub.networkMapCache.notaryIdentities.single()

        // get the membership from the node's membership cache
        val membershipsCacheHolderService = serviceHub.cordaService(MembershipsCacheHolder::class.java)
        val ourMembership = membershipsCacheHolderService.cache.getMembership(bno, ourIdentity)

        if (ourMembership == null) {
           throw FlowException("Invalid membership: ourMembership is null")
        }

        val builder = TransactionBuilder(notary)
                .addReferenceState(ReferencedStateAndRef(ourMembership))
                .addInputState(sampleState)
                .addOutputState(SampleState(partyToTransferTo))
                .addCommand(SampleContract.Commands.Transfer(), ourIdentity.owningKey, partyToTransferTo.owningKey)

        // create a session
        val counterPartySession = initiateFlow(partyToTransferTo)
        // requesting other party to send us their billing states
        counterPartySession.send(RequestMembershipForTransaction(bno))


        val counterPartyMembershipState = subFlow(ReceiveStateAndRefFlow<MembershipState<Any>>(counterPartySession)).single()
        builder.addReferenceState(ReferencedStateAndRef(counterPartyMembershipState))

        // verifying the transaction
        builder.verify(serviceHub)

        val selfSignedTx = serviceHub.signInitialTransaction(builder)

        val allSignedTx = subFlow(CollectSignaturesFlow(selfSignedTx, listOf(counterPartySession)))
        return subFlow(FinalityFlow(allSignedTx, listOf(counterPartySession)))
    }
}

@InitiatedBy(TransferAssetFlow::class)
class TransferAssetFlowResponder(val session : FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {

        // receiving request for billing state
        val bno = session.receive<RequestMembershipForTransaction>().unwrap { it }

        val membershipCacheHolder = serviceHub.cordaService(MembershipsCacheHolder::class.java)
        val membershipState =  membershipCacheHolder.cache.getMembership(bno.bno, ourIdentity)

        if (membershipState == null) {
            throw FlowException ("Invalid membership")
        }

        // sending the states to the requester
        subFlow(SendStateAndRefFlow(session, listOf(membershipState)))

        val stx = subFlow(object : SignTransactionFlow(session) {
            override fun checkTransaction(stx : SignedTransaction) {
                stx.toLedgerTransaction(serviceHub, false).verify()
            }
        })

        subFlow(ReceiveFinalityFlow(session, stx.id))
    }
}

