package net.corda.cordaupdates.app

import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import java.util.*

@CordaService
class ClientConfiguration(private val serviceHub : AppServiceHub) : SingletonSerializeAsToken() {
    companion object {
        const val PROPERTIES_PREFIX = "client-corda-updates"
        const val PROPERTIES_FILE_NAME = "client-corda-updates.properties"
    }
    val config = readProps(PROPERTIES_FILE_NAME).toMap()

    private fun readProps(fileName : String) : Map<String, String> {
        val input = ClientConfiguration::class.java.classLoader.getResourceAsStream(fileName)
        val props = Properties()
        props.load(input)
        return props.propertyNames().toList().map { it as String }.map { it to props.getProperty(it)!!}.toMap()
    }
}

