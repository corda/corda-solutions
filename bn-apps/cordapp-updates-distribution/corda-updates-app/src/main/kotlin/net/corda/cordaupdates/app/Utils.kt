package net.corda.cordaupdates.app

import net.corda.businessnetworks.cordaupdates.core.SyncerConfiguration
import net.corda.core.node.ServiceHub
import java.io.File

object Utils {
    fun syncerConfiguration(serviceHub : ServiceHub) : SyncerConfiguration {
        val configuration = serviceHub.cordaService(ClientConfiguration::class.java)
        val syncerConfigPath = configuration.syncerConfig()
        return if (syncerConfigPath == null) SyncerConfiguration.readFromDefaultLocations() else SyncerConfiguration.readFromFile(File(syncerConfigPath))
    }
}