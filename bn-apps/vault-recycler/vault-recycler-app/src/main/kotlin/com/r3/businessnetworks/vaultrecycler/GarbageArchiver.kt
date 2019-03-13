package com.r3.businessnetworks.vaultrecycler

import net.corda.core.contracts.Attachment
import net.corda.core.transactions.SignedTransaction

class GarbageArchiver(private val transactionChunkSize : Int, private val txProvider : TransactionsProvider) {

    fun archive (recyclableData : RecyclableData, archiver : Archiver) {
        // TODO: implement checkpointing
        for (i in (0..recyclableData.transactions.size) step transactionChunkSize) {
            val idsChunk = recyclableData.transactions.subList(i, i + transactionChunkSize)
            val transactionsChunk = txProvider.getTransactions(idsChunk)
            archiver.archiveTransactions(transactionsChunk)
        }

        // TODO: implement checkpointing
        for (attachmentId in recyclableData.attachments) {
            val attachment = txProvider.getAttachment(attachmentId)
            archiver.archiveAttachment(attachment)
        }
    }
}

interface Archiver {
    fun archiveTransactions(transactions : List<SignedTransaction>)
    fun archiveAttachment(attachment : Attachment)
}