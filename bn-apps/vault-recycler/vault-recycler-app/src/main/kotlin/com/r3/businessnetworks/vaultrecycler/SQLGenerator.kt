package com.r3.businessnetworks.vaultrecycler

class SQLGenerator {
//    private val transactionQueries = listOf(
//            "delete from NODE_TRANSACTIONS where TX_ID=",
//            "delete from NODE_SCHEDULED_STATES where TRANSACTION_ID=",
//            "delete from STATE_PARTY where TRANSACTION_ID=",
//            "delete from VAULT_FUNGIBLE_STATES where TRANSACTION_ID=",
//            "delete from VAULT_FUNGIBLE_STATES_PARTS where TRANSACTION_ID=",
//            "delete from VAULT_LINEAR_STATES where TRANSACTION_ID=",
//            "delete from VAULT_LINEAR_STATES_PARTS where TRANSACTION_ID=",
//            "delete from VAULT_STATES where TRANSACTION_ID=",
//            "delete from VAULT_TRANSACTION_NOTES where TRANSACTION_ID="
//    )

    fun generate(data : RecyclableData) {

    }
}


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