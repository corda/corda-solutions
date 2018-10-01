package net.corda.cordaupdates.app

import net.corda.businessnetworks.cordaupdates.core.SyncerConfiguration
import net.corda.businessnetworks.cordaupdates.core.SyncerTask
import net.corda.core.serialization.SerializationWhitelist

class CordaUpdatesSerialisationWhitelist : SerializationWhitelist {
    override val whitelist = listOf(SyncerConfiguration::class.java, SyncerTask::class.java)
}