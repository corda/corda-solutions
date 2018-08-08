package net.corda.businessnetworks.ledgersync

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

/**
 * A trivial flow that is merely used to illustrate synchronisation by persisting meaningless transactions in
 * participant's vaults
 */
@InitiatingFlow
class BogusFlow(
        us: Party,
        private val them: Party,
        override val linearId: UniqueIdentifier = UniqueIdentifier()
) : FlowLogic<SignedTransaction>(), LinearState {
    override val participants: List<AbstractParty> = listOf(us, them)

    @Suspendable
    override fun call(): SignedTransaction {
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        val cmd = Command(BogusContract.Commands.Bogus(), listOf(them.owningKey))

        val txBuilder = TransactionBuilder(notary)
                .addOutputState(BogusState(listOf(ourIdentity, them)), BOGUS_CONTRACT_ID)
                .addCommand(cmd).apply {
                    verify(serviceHub)
                }

        val partiallySigned = serviceHub.signInitialTransaction(txBuilder)

        val fullySigned = subFlow(CollectSignaturesFlow(partiallySigned, setOf(initiateFlow(them))))

        return subFlow(FinalityFlow(fullySigned))
    }
}

@InitiatedBy(BogusFlow::class)
class BogusFlowFlowResponder(val flowSession: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        subFlow(object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) {
                // accept everything. this is a simple test fixture only.
            }
        })
    }
}

class BogusState(override val participants: List<AbstractParty>) : ContractState