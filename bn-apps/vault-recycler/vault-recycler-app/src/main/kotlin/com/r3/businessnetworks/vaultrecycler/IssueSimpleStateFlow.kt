package com.r3.businessnetworks.vaultrecycler

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

@InitiatingFlow
class IssueSimpleStateFlow(
        private val statesToConsume : List<StateAndRef<SimpleState>>,
        private val statesToIssue : List<SimpleState>
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call() : SignedTransaction {
        val builder = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.single())
        statesToConsume.forEach { builder.addInputState(it) }
        statesToIssue.forEach { builder.addOutputState(it, SimpleContract.CONTRACT_ID) }
        builder.addCommand(SimpleContract.SimpleCommand(), ourIdentity.owningKey)
        val stx = serviceHub.signInitialTransaction(builder)
        return subFlow(FinalityFlow(stx, listOf()))
    }
}