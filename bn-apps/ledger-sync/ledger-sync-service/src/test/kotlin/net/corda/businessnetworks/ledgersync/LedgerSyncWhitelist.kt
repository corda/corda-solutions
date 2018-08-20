package net.corda.businessnetworks.ledgersync

import net.corda.core.serialization.SerializationWhitelist
import java.util.concurrent.ConcurrentHashMap

class LedgerSyncWhitelist : SerializationWhitelist {
    override val whitelist: List<Class<*>> = listOf(ConcurrentHashMap::class.java)
}