package net.corda.cordaupdates.app.member

import net.corda.businessnetworks.cordaupdates.core.SyncerConfiguration
import net.corda.businessnetworks.cordaupdates.core.CordappSource
import net.corda.core.serialization.SerializationWhitelist

class CordaUpdatesSerialisationWhitelist : SerializationWhitelist {
    override val whitelist = listOf(SyncerConfiguration::class.java, CordappSource::class.java)
}