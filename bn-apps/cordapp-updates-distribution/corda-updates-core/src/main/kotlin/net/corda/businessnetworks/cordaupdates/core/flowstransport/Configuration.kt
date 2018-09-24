package net.corda.businessnetworks.cordaupdates.core.flowstransport

import net.corda.core.identity.CordaX500Name
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import java.util.*

@CordaService
class BNOConfigurationService(private val serviceHub : AppServiceHub) : SingletonSerializeAsToken() {
    companion object {
        const val PROPERTIES_FILE_NAME = "corda-updates.properties"
        const val TRANSPORT = "corda-updates.transport"
        const val REMOTE_REPO_URL = "corda-updates.remoteRepoUrl"
    }
    private var _config = readProps(PROPERTIES_FILE_NAME).toMutableMap()

    fun transport() : String = _config[TRANSPORT]!!
    fun remoteRepoUrl() : String = _config[REMOTE_REPO_URL]!!
}

@CordaService
class NodeConfigurationService(private val serviceHub : AppServiceHub) : SingletonSerializeAsToken() {
    companion object {
        const val PROPERTIES_FILE_NAME = "corda-updates.properties"
        const val BNO_NAME = "corda-updates.bnoName"
        const val RPC_PORT = "corda-updates.rpcPort"
    }
    private var _config = readProps(PROPERTIES_FILE_NAME).toMutableMap()

    private fun bnoName() : CordaX500Name = CordaX500Name.parse(_config[BNO_NAME]!!)
    fun bnoParty() = serviceHub.identityService.wellKnownPartyFromX500Name(bnoName())!!
    fun rpcPort() : Int = Integer.valueOf(_config[RPC_PORT]!!)
}


fun readProps(fileName : String) : Map<String, String> {
    val input = NodeConfigurationService::class.java.classLoader.getResourceAsStream(fileName)
    val props = Properties()
    props.load(input)
    return props.propertyNames().toList().map { it as String }.map { it to props.getProperty(it)!!}.toMap()
}
