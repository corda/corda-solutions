package com.r3.businessnetworks.vaultrecycler

import net.corda.core.contracts.Attachment
import net.corda.core.crypto.SecureHash
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.SignedTransaction

interface TransactionsProvider {
    fun transactionsSnapshot() : List<SignedTransaction>
    fun getTransaction(id : SecureHash) : SignedTransaction?
    fun getTransactions(ids : List<SecureHash>) : List<SignedTransaction>
    fun getAttachment(id : SecureHash) : Attachment
}

class FlowsTransactionsProvider(private val serviceHub : ServiceHub) : TransactionsProvider {
    override fun getAttachment(id : SecureHash) : Attachment {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getTransaction(id : SecureHash) : SignedTransaction? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getTransactions(ids : List<SecureHash>) : List<SignedTransaction> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun transactionsSnapshot() : List<SignedTransaction> = serviceHub.validatedTransactions.track().snapshot

}

class RPCTransactionsProvider(private val rpcClient : CordaRPCOps) : TransactionsProvider {
    override fun getAttachment(id : SecureHash) : Attachment {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getTransactions(ids : List<SecureHash>) : List<SignedTransaction> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getTransaction(id : SecureHash) : SignedTransaction? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun transactionsSnapshot() : List<SignedTransaction> = rpcClient.internalVerifiedTransactionsSnapshot()
}