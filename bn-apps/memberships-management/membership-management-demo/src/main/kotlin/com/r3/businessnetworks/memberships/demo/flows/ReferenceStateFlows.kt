package com.r3.businessnetworks.memberships.demo.flows


import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.flows.bno.service.BNOConfigurationService
import com.r3.businessnetworks.membership.flows.member.GetMembershipsFlow
import com.r3.businessnetworks.membership.states.MembershipState
import com.r3.businessnetworks.memberships.demo.contracts.AssetContractWithReferenceStates
import com.r3.businessnetworks.memberships.demo.contracts.AssetStateWithReferenceStates
import net.corda.core.contracts.Amount
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.workflows.asset.CashUtils
import java.util.*

/**
 * The flow obtains the bno from the membership-service-current-bno.conf and checks if the BNO is
 * whitelisted in membership-service.conf. Then the flow issues a new asset to the ledger.
 * The transaction includes the membership as a reference state. This approach provides contractual guarantees
 * wrt to business network membership. Check [AssetContractWithReferenceStates] for details.
 *
 * @return FlowLogic<SignedTransaction>
 *
 **/
class IssueAssetWithReferenceStatesFlow : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val bno = serviceHub.cordaService(CurrentBNOConfigurationService::class.java).bnoParty()
        // check if bno is whitelisted
        checkIfBNOWhitelisted(bno, serviceHub)
        // for the sake of demo we are just taking the first available notary
        val notary = serviceHub.networkMapCache.notaryIdentities.single()

        // get the node's membership (from the node's membership cache or from the bno)
        val ourMembership = subFlow(GetMembershipsFlow(bno))[ourIdentity]
                ?: throw FlowException("Membership for $ourIdentity has not been found")

        val builder = TransactionBuilder(notary)
                .addOutputState(AssetStateWithReferenceStates(ourIdentity), AssetContractWithReferenceStates.CONTRACT_NAME)
                .addCommand(AssetContractWithReferenceStates.Commands.Issue(), ourIdentity.owningKey)
                .addReferenceState(ReferencedStateAndRef(ourMembership))

        builder.verify(serviceHub)
        val stx = serviceHub.signInitialTransaction(builder)
        return subFlow(FinalityFlow(stx, listOf()))
    }
}

/**
 * This is the main flow of this demo app.
 * The flow obtains the bno from the membership-service-current-bno.conf and then checks if the BNO is
 * whitelisted in membership-service.conf. From membership-service.conf it also obtains the notary.
 * Next, the flow issues [amount] Cash to the ledger and then transfers the Cash and the asset [assetState]
 * to the counterParty.
 * The flow first obtains the membership of the current node and includes it in the transaction as a reference state.
 * Then the flow pulls the membership of the counterparty's transaction along with the tx chain which is needed
 * for the xt resolution. Then, this membership is also included as a reference state to the transaction.
 * There is a contractual proof for the membership. Check [AssetContractWithReferenceStates] for details.
 * Upon notarisation this approach guarantees that the memberships where active when the tx got committed to the ledger.
 * Important: The membership status is checked at the counterparty. The assumption here is that
 * This differs from the implementation provided in CounterPartyCheckFlows and ExtendsClassFlows where it is possible that
 * the membership has been to [MembershipStatus.SUSPENDED] or [MembershipStatus.PENDING] in the meanwhile.
 *
 * @param counterParty the counter party to transfer the contract and the cash
 * @param assetState the asset to be transferred to counterParty
 * @param amount of Cash to be issued in this flow and then transferred to counterparty
 * @return FlowLogic<SignedTransaction>
 *
 **/
@InitiatingFlow
class TransferAssetWithReferenceStatesFlow(
        private val counterParty: Party,
        private val assetState: StateAndRef<AssetStateWithReferenceStates>,
        private val amount: Amount<Currency>) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        if (ourIdentity != assetState.state.data.owner)
            throw FlowException("Only owner of the state can transfer it")
        if (ourIdentity == counterParty)
            throw FlowException("sender and recipient should be different parties")

        val bno = serviceHub.cordaService(CurrentBNOConfigurationService::class.java).bnoParty()
        // check if bno is whitelisted
        checkIfBNOWhitelisted(bno, serviceHub)

        val configuration = serviceHub.cordaService(BNOConfigurationService::class.java)
        val notary = configuration.notaryParty()

        // issue money
        val issueRef = OpaqueBytes.of(0)
        subFlow(CashIssueFlow(amount, issueRef, notary))

        // get the node's membership (from the node's membership cache or from the bno)
        val ourMembership = subFlow(GetMembershipsFlow(bno))[ourIdentity]
                ?: throw FlowException("Membership for $ourIdentity has not been found")

        // build the transaction, note that the membership is included as a reference state.
        val outputState = AssetStateWithReferenceStates(counterParty)
        val builder = TransactionBuilder(notary)
                .addReferenceState(ReferencedStateAndRef(ourMembership))
                .addInputState(assetState)
                .addOutputState(outputState)
                .addCommand(AssetContractWithReferenceStates.Commands.Transfer(), ourIdentity.owningKey, counterParty.owningKey)

        // transfer the money to the counterparty
        CashUtils.generateSpend(serviceHub, builder, amount, ourIdentityAndCert, counterParty)

        // create a session with the counterparty
        val counterPartySession = initiateFlow(counterParty)
        // requesting other party to send us their membership state
        // Important: pulls the transaction chain which is needed to the tx resolution
        val counterPartyMembershipState =
                subFlow(ReceiveStateAndRefFlow<MembershipState<Any>>(counterPartySession)).single()
        // the counterparty membership state is included as a reference state to the transaction
        builder.addReferenceState(ReferencedStateAndRef(counterPartyMembershipState))
        // verifying the transaction
        builder.verify(serviceHub)
        // self-sign the transaction
        val selfSignedTx = serviceHub.signInitialTransaction(builder)
        // get the tx singend by the counter party
        val allSignedTx = subFlow(CollectSignaturesFlow(selfSignedTx, listOf(counterPartySession)))
        // notarise the transaction
        return subFlow(FinalityFlow(allSignedTx, listOf(counterPartySession)))
    }
}

@InitiatedBy(TransferAssetWithReferenceStatesFlow::class)
/**
 * This is the responder for the above flow.
 * The flow obtains the bno from CurrentBNOConfigurationService and then checks if the BNO is whitelisted
 * in membership-service.conf. From membership-service.conf it also obtains the notary.
 * The flow obtains their membership and then sends it back along with the associated tx chain so that it
 * can be validated on the initiators side. Finally, it signs the transaction and returns it to the initiating flow.
 *
 * @param session this is the session between the two involved parties.
 * @return FlowLogic<Unit>
 *
 **/
class TransferAssetWithReferenceStatesFlowResponder(val session: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val bno = serviceHub.cordaService(CurrentBNOConfigurationService::class.java).bnoParty()

        // check if bno is whitelisted
        checkIfBNOWhitelisted(bno, serviceHub)

        val ourMembership = subFlow(GetMembershipsFlow(bno))[ourIdentity]
                ?: throw FlowException("Membership for $ourIdentity has not been found")

        // sending the membership state along with the transaction chain to the requester
        subFlow(SendStateAndRefFlow(session, listOf(ourMembership)))
        // sign the transaction
        val stx = subFlow(object : SignTransactionFlow(session) {
            override fun checkTransaction(stx: SignedTransaction) {
                stx.toLedgerTransaction(serviceHub, false).verify()
            }
        })
        subFlow(ReceiveFinalityFlow(session, stx.id))
    }
}