package com.r3.businessnetworks.ledgersync

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.commons.SupportFinalityFlow
import com.r3.businessnetworks.commons.SupportReceiveFinalityFlow
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.Command
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

/**
 * A trivial flow that is merely used to illustrate synchronisation by persisting meaningless transactions in
 * participant's vaults
 */
@InitiatingFlow
@StartableByRPC
class BogusFlow(
        private val them: Party
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        val cmd = Command(BogusContract.Commands.Bogus(), listOf(them.owningKey))

        val txBuilder = TransactionBuilder(notary)
                .addOutputState(BogusState(ourIdentity, them), BOGUS_CONTRACT_ID)
                .addCommand(cmd).apply {
                    verify(serviceHub)
                }

        val partiallySigned = serviceHub.signInitialTransaction(txBuilder)

        val session = initiateFlow(them)

        val fullySigned = subFlow(CollectSignaturesFlow(partiallySigned, setOf(session)))

        return subFlow(SupportFinalityFlow(fullySigned) {
            listOf(session)
        })
    }
}

@InitiatedBy(BogusFlow::class)
class BogusFlowFlowResponder(val flowSession: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val selfSignedTx = subFlow(object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) {
            }
        })
        subFlow(SupportReceiveFinalityFlow(flowSession, selfSignedTx.id))
    }
}

@BelongsToContract(BogusContract::class)
data class BogusState(
        private val us: Party,
        private val them: Party,
        override val linearId: UniqueIdentifier = UniqueIdentifier()
) : LinearState {
    override val participants: List<AbstractParty> = listOf(us, them)
}