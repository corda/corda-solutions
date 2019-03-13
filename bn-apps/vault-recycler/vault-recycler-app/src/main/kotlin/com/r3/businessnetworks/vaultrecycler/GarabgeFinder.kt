package com.r3.businessnetworks.vaultrecycler

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction

class FindGarbageFlow : FlowLogic<RecyclableData>() {

    @Suspendable
    override fun call() : RecyclableData {
        val allTxs = serviceHub.validatedTransactions.track().snapshot
        return RecyclableDataFiner(allTxs).find()
    }

}

class RecyclableDataFiner(transactionSnapshot : List<SignedTransaction>) {
    private val allTxs : MutableList<SignedTransaction> = transactionSnapshot.toMutableList()
    private lateinit var allAttachments : MutableList<SecureHash>

    fun find() : RecyclableData {
        val txByInput = allTxs.flatMap { stx -> stx.inputs.map { it to stx } }.toMap()
        val txByOutput = allTxs.flatMap { stx -> stx.tx.outputs.mapIndexed { index, _ -> StateRef(stx.id, index) to stx } }.toMap()
        allAttachments = allTxs.flatMap { it.tx.attachments }.toMutableList()

        val txsWithUnspentOutputs = allTxs
                // creating a map of tx by output state
                .flatMap { stx -> stx.tx.outputs.mapIndexed { ind, value -> StateAndRef(value, StateRef(stx.id, ind)) to stx } }
                // filtering out all outputs that have been consumed as inputs in other transactions
                .filter { !txByInput.containsKey(it.first.ref) }
                .map { it.second }.toSet()

        // removing all preceding transactions
        txsWithUnspentOutputs
                .forEach { dfs(txByOutput, it) }

        return RecyclableData(allTxs.map { it.id }.sorted(), allAttachments.sorted())
    }


    private fun dfs(txByOutput: Map<StateRef, SignedTransaction>,
                    stx : SignedTransaction) {
        // removing the transaction itself
        allTxs.remove(stx)
        // removing all attachments
        stx.tx.attachments.forEach { allAttachments.remove(it) }
        for (input in stx.tx.inputs) {
            val previousTx = txByOutput[input]
            if (previousTx != null) {
                dfs(txByOutput, previousTx)
            }
        }
    }
}


@CordaSerializable
data class RecyclableData(
        val transactions : List<SecureHash>,
        val attachments : List<SecureHash>)
//
//
//val encoder = Base64.getEncoder().withoutPadding()!!
//val decoder = Base64.getDecoder()
//
//private fun Any.secureHash() : SecureHash = SecureHash.sha256(this.serialize().bytes)
//private fun Any.serialiseAndBase64() = encoder.encodeToString(this.serialize().bytes)
//private fun BufferedWriter.writeLn(text : String) {
//    write(text)
//    newLine()
//}
//inline fun <reified T : Any>  String.unbase64AndDeserialise() : T= decoder.decode(this).deserialize()
//
