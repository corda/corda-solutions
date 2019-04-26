package com.r3.businessnetworks.memberships.demo.flows


import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.flows.bno.service.BNOConfigurationService
import com.r3.businessnetworks.membership.flows.member.support.BusinessNetworkAwareInitiatedFlow
import com.r3.businessnetworks.membership.flows.member.support.BusinessNetworkAwareInitiatingFlow
import com.r3.businessnetworks.memberships.demo.contracts.AssetContractCounterPartyChecks
import com.r3.businessnetworks.memberships.demo.contracts.AssetStateCounterPartyChecks
import net.corda.core.contracts.Amount
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
 * The flow checks if the BNO is whitelisted in membership-service.conf. Then the flow issues a new asset to the ledger.
 * The flow extends the BusinessNetworkAwareInitiatingFlow which checks whether the bno is whitelisted.
 *
 * @param bno the business network operator
 * @return FlowLogic<SignedTransaction>
 *
 **/
class IssueAssetExtendClassFlow(bno: Party) : BusinessNetworkAwareInitiatingFlow<SignedTransaction>(bno) {
    @Suspendable
    override fun afterBNOIdentityVerified(): SignedTransaction {
        val notary = serviceHub.cordaService(BNOConfigurationService::class.java).notaryParty()

        val builder = TransactionBuilder(notary)
                .addOutputState(AssetStateCounterPartyChecks(ourIdentity), AssetContractCounterPartyChecks.CONTRACT_NAME)
                .addCommand(AssetContractCounterPartyChecks.Commands.Issue(), ourIdentity.owningKey)

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
 * Important: The membership status is checked at the counterparty. The assumption here is that
 * the caller "knows" that the counterparty has a member for this business network and thus is not
 * checking it. In doing so the flow extends BusinessNetworkAwareInitiatingFlow.
 *
 * @param bno the Business Network Operator
 * @param counterParty the counter party to transfer the contract and the cash
 * @param assetState the asset to be transferred to counterParty
 * @param amount of Cash to be issued in this flow and then transferred to counterparty
 * @return FlowLogic<SignedTransaction>
 *
 **/
@InitiatingFlow
class TransferAssetExtendClassFlow(
        bno: Party,
        private val counterParty: Party,
        private val assetState: StateAndRef<AssetStateCounterPartyChecks>,
        private val amount: Amount<Currency>) : BusinessNetworkAwareInitiatingFlow<SignedTransaction>(bno) {

    @Suspendable
    override fun afterBNOIdentityVerified(): SignedTransaction {
        if (ourIdentity != assetState.state.data.owner)
            throw FlowException("Only owner of the state can transfer it")
        if (ourIdentity == counterParty)
            throw FlowException("sender and recipient should be different parties")

        // get the notary
        val notary = serviceHub.cordaService(BNOConfigurationService::class.java).notaryParty()
        // issue money
        val issueRef = OpaqueBytes.of(0)
        subFlow(CashIssueFlow(amount, issueRef, notary))

        // build the transaction
        val outputState = AssetStateCounterPartyChecks(counterParty)
        val builder = TransactionBuilder(notary)
                .addInputState(assetState)
                .addOutputState(outputState)
                .addCommand(AssetContractCounterPartyChecks.Commands.Transfer(), ourIdentity.owningKey, counterParty.owningKey)

        // transfer Cash to counterparty
        CashUtils.generateSpend(serviceHub, builder, amount, ourIdentityAndCert, counterParty)

        // verifying the transaction
        builder.verify(serviceHub)
        // self-sign transaction
        val selfSignedTx = serviceHub.signInitialTransaction(builder)
        // create a session with the counterparty
        val counterPartySession = initiateFlow(counterParty)
        // collect signatures
        val allSignedTx = subFlow(CollectSignaturesFlow(selfSignedTx, listOf(counterPartySession)))
        // notarise the transaction
        return subFlow(FinalityFlow(allSignedTx, listOf(counterPartySession)))
    }
}

/**
 * This is the responder for the above flow.
 * The flow obtains the bno from CurrentBNOConfigurationService and then checks if the BNO is whitelisted in
 * membership-service.conf. From membership-service.conf it also obtains the notary.
 * The flow checks the membership of the counterparty. Then signs the transaction and return it to the initiating flow.
 * This behaviour mimics what happens in the ExtendClassFlows.kt which leverages BusinessNetworkAwareInitiatedFlow
 * and BusinessNetworkAwareInitiatingFlow.
 *
 * @param session this is the session between the two involved parties.
 * @return FlowLogic<Unit>
 *
 **/
@InitiatedBy(TransferAssetExtendClassFlow::class)
class TransferAssetExtendClassFlowResponder(val session: FlowSession) : BusinessNetworkAwareInitiatedFlow<Unit>(session) {

    override fun bnoIdentity() = serviceHub.cordaService(CurrentBNOConfigurationService::class.java).bnoParty()

    @Suspendable
    override fun onOtherPartyMembershipVerified() {
        val stx = subFlow(object : SignTransactionFlow(session) {
            override fun checkTransaction(stx: SignedTransaction) {
                stx.toLedgerTransaction(serviceHub, false).verify()
            }
        })
        subFlow(ReceiveFinalityFlow(session, stx.id))
    }
}