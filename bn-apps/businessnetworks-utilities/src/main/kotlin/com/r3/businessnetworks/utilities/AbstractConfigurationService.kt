package com.r3.businessnetworks.utilities

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.AppServiceHub
import net.corda.core.serialization.SingletonSerializeAsToken
import java.io.File
import java.nio.file.Paths

abstract class AbstractConfigurationService(val appServiceHub : AppServiceHub, val configName : String) : SingletonSerializeAsToken() {
    companion object {
        private const val BNO_NAME = "bnoName"
        private const val NOTARY_NAME = "notaryName"
    }

    protected var _config = loadConfig()

    /**
     * Intended to be used in tests only
     */
    fun reloadConfigurationFromFile(file : File) {
        _config = ConfigFactory.parseFile(file)
    }

    open fun bnoName() = CordaX500Name.parse(_config.getString(BNO_NAME))
    open fun bnoParty() = getPartyOrThrowException(bnoName())
    open fun notaryName() = CordaX500Name.parse(_config.getString(NOTARY_NAME))
    open fun notaryParty() = getPartyOrThrowException(notaryName())

    fun getPartyOrThrowException(name : CordaX500Name) : Party {
        return appServiceHub.networkMapCache.getPeerByLegalName(name)
                ?: throw IllegalArgumentException("Party $name has not been found on the network")
    }

    fun getNotaryPartyOrThrowException(name : CordaX500Name) : Party {
        return appServiceHub.networkMapCache.getNotary(name)
                ?: throw IllegalArgumentException("Notary $name has not been found on the network")
    }

    private fun loadConfig() : Config {
        val fileName = "$configName.conf"
        val defaultLocation = (Paths.get("cordapps").resolve("config").resolve(fileName)).toFile()
        return if (defaultLocation.exists()) ConfigFactory.parseFile(defaultLocation)
        else ConfigFactory.parseFile(File(AbstractConfigurationService::class.java.classLoader.getResource(fileName).toURI()))
    }
}