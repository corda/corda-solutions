package net.corda.businessnetworks.ledgersync

import net.corda.core.contracts.ContractState
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault.StateStatus.ALL
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.MAX_PAGE_SIZE
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria.VaultQueryCriteria
import sun.security.util.ByteArrayLexOrder

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
fun VaultService.withParticipants(vararg parties: Party): List<SecureHash> = queryBy<ContractState>(
        VaultQueryCriteria(status = ALL),
        PageSpecification(1, MAX_PAGE_SIZE)
).states.filter {
    it.state.data.participants.containsAll(parties.toList())
}.map {
    it.ref.txhash
}

/**
 * Calculates a compound hash of multiple hashes by hashing their concatenation in lexical order.
 */
fun List<SecureHash>.hash(): SecureHash = map {
    it.bytes
}.sortedWith(ByteArrayLexOrder()).fold(ByteArray(0)) { acc, hash ->
    acc + hash
}.sha256()