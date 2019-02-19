package com.r3.businessnetworks.garbagecollector

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.flows.FlowLogic
import net.corda.core.transactions.SignedTransaction

class FindGarbageFlow : FlowLogic<Set<SignedTransaction>>() {
    lateinit var allTxs : MutableSet<SignedTransaction>


    @Suspendable
    override fun call() : Set<SignedTransaction> {
        allTxs = serviceHub.validatedTransactions.track().snapshot.toMutableSet()
        val txByInput = allTxs.flatMap { stx -> stx.inputs.map { it to stx } }.toMap()
        val txByOutput = allTxs.flatMap { stx -> stx.tx.outputs.mapIndexed { index, _ -> StateRef(stx.id, index) to stx } }.toMap()

        val txsWithUnspentOutputs = allTxs
                // creating a map of tx by output state
                .flatMap { stx -> stx.tx.outputs.mapIndexed { ind, value -> StateAndRef(value, StateRef(stx.id, ind)) to stx } }
                // filtering out all outputs that have been consumed as inputs in other transactions
                .filter { !txByInput.containsKey(it.first.ref) }
                .map { it.second }.toSet()

        // removing all preceding transactions
        txsWithUnspentOutputs
                .forEach { removePrecedingTransactions(txByInput, txByOutput, it) }

        return allTxs
    }

    @Suspendable
    private fun removePrecedingTransactions(txByInput: Map<StateRef, SignedTransaction>,
                                            txByOutput: Map<StateRef, SignedTransaction>,
                                            stx : SignedTransaction) {
        removeSubsequentTransactions(txByInput, stx)
        for (input in stx.tx.inputs) {
            val previousTx = txByOutput[input]
            if (previousTx != null) {
                removePrecedingTransactions(txByInput, txByOutput, previousTx)
            }
        }
    }

    @Suspendable
    private fun removeSubsequentTransactions(txByInput: Map<StateRef, SignedTransaction>, stx : SignedTransaction) {
        // if we've already been here - not visiting again
        if (!allTxs.remove(stx)) {
            return
        }
        for (outputIndex in stx.tx.outputs.indices) {
            val outputStateRef = StateRef(stx.id, outputIndex)
            val nextTx = txByInput[outputStateRef]
            if (nextTx != null) {
                removeSubsequentTransactions(txByInput, nextTx)
            }
        }
    }
}