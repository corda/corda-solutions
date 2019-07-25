package com.r3.businessnetworks.ledgersync

import net.corda.core.contracts.ContractState
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault.StateStatus.ALL
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.DEFAULT_PAGE_SIZE
import net.corda.core.node.services.vault.MAX_PAGE_SIZE
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.QueryCriteria.VaultQueryCriteria
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.deserialize
import net.corda.core.transactions.SignedTransaction
import sun.security.util.ByteArrayLexOrder
import java.lang.StringBuilder
import java.sql.Blob
import java.util.*

/**
 * Provides a list of transaction hashes referring to transactions in which all of the given parties are participating.
 *
 * Due to limitations of filtering by [Party] in vault query criteria (CORDA-1888), this will load ALL STATES into
 * memory, potentially causing memory issues at the node running the query. A proper vault query criterion should be
 * used once implemented.
 *
 * Note that this can be a lengthy list and no precautions are taken to ensure the output does not exceed the maximum
 * message size.
 */

fun VaultService.withParticipants(vararg parties: Party): List<SecureHash> {
    val list = mutableListOf<SecureHash>()
    val pageSize = DEFAULT_PAGE_SIZE
    val criteria = VaultQueryCriteria(status = ALL)
    var count = 1
    var page = queryBy<ContractState>(criteria, PageSpecification(count, pageSize))
    while(page.states.isNotEmpty()) {
        page.states.filter {
            it.state.data.participants.containsAll(parties.toList())
        }.map {
            list.add(it.ref.txhash)
        }
        count ++
        page = queryBy(criteria, PageSpecification(count, pageSize))
    }
    return list
}

/**
 * Calculates a compound hash of multiple hashes by hashing their concatenation in lexical order.
 */
fun List<SecureHash>.hash(): SecureHash = map {
    it.bytes
}.sortedWith(ByteArrayLexOrder()).fold(ByteArray(0)) { acc, hash ->
    acc + hash
}.sha256()
