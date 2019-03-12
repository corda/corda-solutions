package com.r3.businessnetworks.vaultrecycler

import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.SignedTransaction
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.sql.Connection
import java.util.*


const val BACKUP_PATH = "vault-recycler"
const val BATCH_SIZE = 1000

class RecyclableDataFiner(transactionSnapshot : List<SignedTransaction>) {
    private val allTxs : MutableList<SignedTransaction> = transactionSnapshot.toMutableList()
    lateinit var allAttachments : MutableList<SecureHash>

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

        return RecyclableData(allTxs.sortedBy { it.id }, allAttachments.sorted())
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


abstract class RecyclableDataArchiver {

    private inline fun <reified T : Any> BufferedReader.readAndProcess(processorFunction : (x : List<T>) -> Unit) {
        val numberOfRecords = readLine()!!.unbase64AndDeserialise<Int>()
        readLine() // skipping the hash
        val currentBatch  = mutableListOf<T>()
        (0..numberOfRecords).forEach {_ ->
            val stx = readLine()!!.unbase64AndDeserialise<T>()
            currentBatch.add(stx)
            if (currentBatch.size >= BATCH_SIZE) {
                processorFunction(currentBatch)
                currentBatch.clear()
            }
        }
    }

    fun archive(backupHash : String) {
        BufferedReader(InputStreamReader(FileInputStream(backupHash))).use { reader ->
            onStart()
            reader.readAndProcess<SignedTransaction> { processTransactionsBatch(it) }
            reader.readAndProcess<SecureHash> { processAttachmentsBatch(it) }
            onFinish()
        }
    }

    abstract fun onStart()
    abstract fun onFinish()
    abstract fun processTransactionsBatch(txs : List<SignedTransaction>)
    abstract fun processAttachmentsBatch(attachments : List<SecureHash>)
}

class RecyclableDataCleaner(val jdbcSession : Connection) {
    private val transactionQueries = listOf(
            "delete from NODE_TRANSACTIONS where TX_ID=",
            "delete from NODE_SCHEDULED_STATES where TRANSACTION_ID=",
            "delete from STATE_PARTY where TRANSACTION_ID=",
            "delete from VAULT_FUNGIBLE_STATES where TRANSACTION_ID=",
            "delete from VAULT_FUNGIBLE_STATES_PARTS where TRANSACTION_ID=",
            "delete from VAULT_LINEAR_STATES where TRANSACTION_ID=",
            "delete from VAULT_LINEAR_STATES_PARTS where TRANSACTION_ID=",
            "delete from VAULT_STATES where TRANSACTION_ID=",
            "delete from VAULT_TRANSACTION_NOTES where TRANSACTION_ID="
    )


    fun clean(backupHash : String) {

    }
}


@CordaSerializable
data class RecyclableData(
        val transactions : List<SignedTransaction>,
        val attachmentIds : List<SecureHash>) {

    fun writeToFile() {
        val txsHash = transactions.map { it.id }.secureHash()
        val attachmentsHash = attachmentIds.secureHash()
        val fileName = listOf(txsHash, attachmentsHash).secureHash().toString()
        BufferedWriter(OutputStreamWriter(FileOutputStream("$BACKUP_PATH/$fileName", false))).use { writer ->
            writer.writeLn(transactions.size.serialiseAndBase64())
            writer.writeLn(txsHash.serialiseAndBase64())
            transactions.forEach { tx ->
                writer.writeLn(tx.serialiseAndBase64())
            }

            writer.writeLn(attachmentIds.size.serialiseAndBase64())
            writer.writeLn(attachmentsHash.serialiseAndBase64())
            attachmentIds.forEach { id ->
                writer.writeLn(id.serialiseAndBase64())
            }
        }
    }
}


private val encoder = Base64.getEncoder().withoutPadding()!!
private val decoder = Base64.getDecoder()

private fun Any.secureHash() : SecureHash = SecureHash.sha256(this.serialize().bytes)
private fun Any.serialiseAndBase64() = encoder.encodeToString(this.serialize().bytes)
private fun BufferedWriter.writeLn(text : String) {
    write(text)
    newLine()
}
inline fun <reified T : Any>  String.unbase64AndDeserialise() : T= decoder.decode(this).deserialize()

//
//all tables have an attachment id column
//
//NODE_ATTACHMENTS
//NODE_ATTACHMENTS_CONTRACTS
//NODE_ATTACHMENTS_SIGNERS
//
//
//conf identities
//
//NODE_OUR_KEY_PAIRS
//
//
//
//NODE_TRANSACTIONS
//NODE_SCHEDULED_STATES
//STATE_PARTY - has transaction id
//VAULT_FUNGIBLE_STATES - by transaction id
//VAULT_FUNGIBLE_STATES_PARTS - by trasaction id
//VAULT_LINEAR_STATES - by transaction id
//VAULT_LINEAR_STATES_PARTS - by transaction id
//VAULT_STATES
//VAULT_TRANSACTION_NOTES