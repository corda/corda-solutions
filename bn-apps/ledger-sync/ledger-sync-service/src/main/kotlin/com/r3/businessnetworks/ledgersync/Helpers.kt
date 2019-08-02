package com.r3.businessnetworks.ledgersync

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.core.contracts.ContractState
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.AppServiceHub
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.Vault.StateStatus.ALL
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.DEFAULT_PAGE_SIZE
import net.corda.core.node.services.vault.MAX_PAGE_SIZE
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.QueryCriteria.VaultQueryCriteria
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.SingletonSerializationToken
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.deserialize
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.loggerFor
import sun.security.util.ByteArrayLexOrder
import java.io.File
import java.lang.StringBuilder
import java.nio.file.Paths
import java.sql.Blob
import java.util.*

/**
 * Provides a list of transaction hashes referring to transactions in which all of the given parties are participating.
 *
 * Due to limitations of filtering by [Party] in vault query criteria (CORDA-3112), this will load ALL STATES into
 * memory page by page. The page size will be either defined in config file or using DEFAULT_PAGE_SIZE.
 * A proper vault query criteria with participants filtering should be used once the above issue is fixed.
 *
 */

fun ServiceHub.withParticipants(vararg parties: Party, pageSize: Int = this.cordaService(ConfigurationService::class.java).pageSize()): List<SecureHash> {
    val list = mutableListOf<SecureHash>()
    val criteria = VaultQueryCriteria(status = ALL)
    var count = 1
    var page = vaultService.queryBy<ContractState>(criteria, PageSpecification(count, pageSize))
    while(page.states.isNotEmpty()) {
        page.states.filter {
            it.state.data.participants.containsAll(parties.toList())
        }.map {
            list.add(it.ref.txhash)
        }
        count ++
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
class ConfigurationService(appServiceHub : AppServiceHub): SingletonSerializeAsToken()  {
    companion object {
        private val logger = loggerFor<ConfigurationService>()
    }
    private val configName = "ledgersync"
    private var _config = loadConfig()
    private fun loadConfig() : Config? {
        val fileName = "$configName.conf"
        val defaultLocation = (Paths.get("cordapps").resolve("config").resolve(fileName)).toFile()
        return if (defaultLocation.exists()) ConfigFactory.parseFile(defaultLocation)
        else {
            val configResource = this::class.java.classLoader.getResource(fileName)
            if (configResource == null) {
                null
            } else ConfigFactory.parseFile(File(configResource.toURI()))
        }
    }
    open fun pageSize() = _config?.let{
        var size = DEFAULT_PAGE_SIZE
        try {
            size = it.getInt("pageSize")
            logger.info("pageSize = ${it.getInt("pageSize")}")
        } catch (e: Exception) {
            logger.warn("pageSize is not properly configured! Exception: ${e.message} \n" +
                    "Using DEFAULT_PAGE_SIZE = $DEFAULT_PAGE_SIZE")
        }
        size
    } ?: run {
        logger.warn("Configuration $configName.conf has not been found.\n" +
                "Using DEFAULT_PAGE_SIZE = $DEFAULT_PAGE_SIZE")
        DEFAULT_PAGE_SIZE
    }
}