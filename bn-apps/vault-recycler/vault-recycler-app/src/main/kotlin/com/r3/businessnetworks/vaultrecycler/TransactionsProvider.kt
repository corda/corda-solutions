package com.r3.businessnetworks.vaultrecycler

import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.SignedTransaction

interface TransactionsProvider {
    fun transactionsSnapshot() : List<SignedTransaction>
}

class FlowsTransactionsProvider(private val serviceHub : ServiceHub) : TransactionsProvider {
    override fun transactionsSnapshot() : List<SignedTransaction> = serviceHub.validatedTransactions.track().snapshot

}

class RPCTransactionsProvider(private val rpcClient : CordaRPCOps) : TransactionsProvider {
    override fun transactionsSnapshot() : List<SignedTransaction> = rpcClient.internalVerifiedTransactionsSnapshot()
}