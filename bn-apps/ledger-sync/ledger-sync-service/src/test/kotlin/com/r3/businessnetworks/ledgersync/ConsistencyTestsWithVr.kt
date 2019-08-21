package com.r3.businessnetworks.ledgersync

import co.paralleluniverse.fibers.Suspendable
import com.r3.vaultrecycler.schemas.DBService
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByService
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.startFlow
import org.junit.Test
import kotlin.test.assertEquals

class ConsistencyTestsWithVr : ConsistencyTests() {
    override val cordappPackages: List<String> = listOf(
            "com.r3.businessnetworks.membership",
            "com.r3.businessnetworks.membership.states",
            "com.r3.businessnetworks.ledgersync",
            "com.r3.vaultrecycler.schemas"
    )

    @Test
    fun `VR is detected`() {
        // comes from abstract class
        val future = node1.fromNetwork().services.startFlow(VaultRecyclerExistFlow()).resultFuture
        mockNetwork.runNetwork()
        assertEquals(true, future.getOrThrow())
    }

    /**
     * ... Others tests that apply to situations where VR is present, only
     */
    @Test
    fun `only one node has been recycled`() {
        node1.fromNetwork().createTransactions(10)

        assertEquals(20, node1.fromNetwork().bogusStateCount())

        // part of the transactions have been recycled
        node1.fromNetwork().simulateVR(4)

        assertEquals(16, node1.fromNetwork().bogusStateCount())

        val ledgerSyncResult = node1.fromNetwork().runRequestLedgerSyncFlow(node1.fromNetwork().regularNodes())

        assertEquals(0, ledgerSyncResult[node2.fromNetwork().identity()]!!.missingAtRequester.size)
        assertEquals(0, ledgerSyncResult[node3.fromNetwork().identity()]!!.missingAtRequester.size)

        // part of the transactions have been recycled, and the rest are lost
        node1.fromNetwork().simulateCatastrophicFailure()

        assertEquals(0, node1.fromNetwork().bogusStateCount())

        val ledgerSyncResult2 = node1.fromNetwork().runRequestLedgerSyncFlow(node1.fromNetwork().regularNodes())

        assertEquals(16, ledgerSyncResult2[node2.fromNetwork().identity()]!!.missingAtRequester.size + ledgerSyncResult2[node3.fromNetwork().identity()]!!.missingAtRequester.size)

        // recovery
        node1.fromNetwork().runTransactionRecoveryFlow(ledgerSyncResult2)

        assertEquals(16, node1.fromNetwork().bogusStateCount())
    }

    @Test
    fun `both nodes have been recycled on different transactions`() {
        node1.fromNetwork().createTransaction(node2.fromNetwork().identity(), 3)

        // both node1 and node2 recycled the transactions created in first batch
        node1.fromNetwork().simulateVR(3)
        node2.fromNetwork().simulateVR(3)

        // only node1 recycled some transactions created in second batch
        node1.fromNetwork().createTransaction(node2.fromNetwork().identity(), 4)
        node1.fromNetwork().simulateVR(2)

        assertEquals(2, node1.fromNetwork().bogusStateCount())
        assertEquals(4, node2.fromNetwork().bogusStateCount())

        val ledgerSyncResult = node1.fromNetwork().runRequestLedgerSyncFlow(listOf(node2.fromNetwork().identity()))

        assertEquals(0, ledgerSyncResult[node2.fromNetwork().identity()]!!.missingAtRequester.size)
        assertEquals(0, ledgerSyncResult[node2.fromNetwork().identity()]!!.missingAtRequestee.size)


        // node1 lost the rest of the transaction
        node1.fromNetwork().simulateCatastrophicFailure()

        assertEquals(0, node1.fromNetwork().bogusStateCount())

        val ledgerSyncResult2 = node1.fromNetwork().runRequestLedgerSyncFlow(listOf(node2.fromNetwork().identity()))

        assertEquals(2, ledgerSyncResult2[node2.fromNetwork().identity()]!!.missingAtRequester.size)
        assertEquals(0, ledgerSyncResult2[node2.fromNetwork().identity()]!!.missingAtRequestee.size)

        // recovery
        node1.fromNetwork().runTransactionRecoveryFlow(ledgerSyncResult2)

        assertEquals(2, node1.fromNetwork().bogusStateCount())
    }

    @Test
    fun `one node has recycled a transaction and the other lost it`() {
        node1.fromNetwork().createTransaction(node2.fromNetwork().identity(), 2)
        node1.fromNetwork().simulateVR(2)
        node2.fromNetwork().simulateCatastrophicFailure()
        node1.fromNetwork().createTransaction(node2.fromNetwork().identity(), 5)

        assertEquals(5, node1.fromNetwork().bogusStateCount())
        assertEquals(5, node1.fromNetwork().bogusStateCount())

        val ledgerSyncResult = node1.fromNetwork().runRequestLedgerSyncFlow(listOf(node2.fromNetwork().identity()))
        assertEquals(0, ledgerSyncResult[node2.fromNetwork().identity()]!!.missingAtRequester.size)
        assertEquals(0, ledgerSyncResult[node2.fromNetwork().identity()]!!.missingAtRequestee.size)


    }

    /**
     * All helper functions that are related to VaultRecycler
     */
    private fun TestStartedNode.simulateVR(num: Int) {
        // remove from node_transactions
        val recycled = this.simulateCatastrophicFailureAndReturnList(num)
        // add to recycled transactions
        this.runVaultRecyclerInsertTxFlow(recycled)
        restart()
    }

    private fun TestStartedNode.runVaultRecyclerInsertTxFlow(list: List<SecureHash>) {
        val future = this.services.startFlow(VaultRecyclerInsertTxFlow(list)).resultFuture
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    private fun TestStartedNode.simulateCatastrophicFailureAndReturnList(num: Int): List<SecureHash> {
        var list = mutableListOf<SecureHash>()
        services.database.transaction {
            connection.prepareStatement("""SELECT transaction_id FROM VAULT_STATES WHERE CONTRACT_STATE_CLASS_NAME='${BogusState::class.java.canonicalName}' limit $num""").executeQuery()
                    .let { results ->
                        while (results.next()) {
                            results.getString(1).let { transactionId ->
                                connection.prepareStatement("""DELETE FROM NODE_TRANSACTIONS WHERE tx_id='$transactionId'""").execute()
                                connection.prepareStatement("""DELETE FROM VAULT_LINEAR_STATES_PARTS WHERE transaction_id='$transactionId'""").execute()
                                connection.prepareStatement("""DELETE FROM VAULT_LINEAR_STATES WHERE transaction_id='$transactionId'""").execute()
                                connection.prepareStatement("""DELETE FROM VAULT_STATES WHERE transaction_id='$transactionId'""").execute()
                                list.add(SecureHash.parse(transactionId))
                            }
                        }
                    }
        }

        return list
    }

    @StartableByService
    private class VaultRecyclerInsertTxFlow(private val list: List<SecureHash>) : FlowLogic<Unit>() {
        @Suspendable
        override fun call(): Unit {

            val dbService = serviceHub.cordaService(DBService::class.java)

            // Check Recyclable Tx
            dbService.createRecyclableTxEntries(list)

        }

    }

}