package com.r3.businessnetworks.ledgersync

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.core.contracts.ContractState
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.identity.Party
import net.corda.core.node.AppServiceHub
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.Vault.StateStatus.ALL
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.DEFAULT_PAGE_SIZE
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria.VaultQueryCriteria
import net.corda.core.serialization.SingletonSerializeAsToken
import sun.security.util.ByteArrayLexOrder
import java.io.File
import java.nio.file.Paths

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

fun ServiceHub.withParticipants(vararg parties: Party, pageSize: Int = this.cordaService(ConfigurationService::class.java).pageSize()): List<SecureHash> {
    val list = mutableListOf<SecureHash>()
    val criteria = VaultQueryCriteria(status = ALL)
    var count = 1
    var page = vaultService.queryBy<ContractState>(criteria, PageSpecification(count, pageSize))
    while (page.states.isNotEmpty()) {
        page.states.filter {
            it.state.data.participants.containsAll(parties.toList())
        }.map {
            list.add(it.ref.txhash)
        }
        count++
        page = vaultService.queryBy(criteria, PageSpecification(count, pageSize))
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

/*
    read pageSize from ledgersync.conf. More config could be added if needed.
 */
@CordaService
class ConfigurationService(appServiceHub: AppServiceHub) : SingletonSerializeAsToken() {
    private val configName = "ledgersync"
    private var _config = loadConfig()
    private fun loadConfig(): Config? {
        val fileName = "$configName.conf"
        val defaultLocation = (Paths.get("cordapps").resolve("config").resolve(fileName)).toFile()
        return if (defaultLocation.exists()) ConfigFactory.parseFile(defaultLocation)
        else {
            val configResource = this::class.java.classLoader.getResource(fileName)
            if (configResource == null) {
                //logger.error("Configuration $configName.conf has not been found")
                null
            } else ConfigFactory.parseFile(File(configResource.toURI()))
        }
    }

    open fun pageSize() = _config?.getInt("pageSize") ?: DEFAULT_PAGE_SIZE
}

fun ServiceHub.getRecycledTx(): List<SecureHash> {
    return if (!vrExist())
        emptyList()
    else
        this.withEntityManager {
            val hqlSelectQuery = "select txID from RecyclableTransaction as dbTable where dbTable.txID not in " +
                    "(select nodeTXTable.txId from DBTransactionStorage\$DBTransaction nodeTXTable) "
            createQuery(hqlSelectQuery)
                    .resultList
                    .map { SecureHash.parse(it as String) }
        }
}

fun ServiceHub.vrExist(): Boolean {
    return this.withEntityManager {
        this.metamodel.entities.any { it.name.equals("RecyclableTransaction") }
    }
}