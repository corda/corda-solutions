package net.corda.cordaupdates.app.member

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import java.time.Instant

/**
 * This flow return an Instant when the artifacts have been synced for the last time.
 * Last sync time is not persisted through node restarts.
 */
class GetLastSyncTimeFlow : FlowLogic<Instant?>() {
    @Suspendable
    override fun call() = serviceHub.cordaService(SyncerService::class.java).lastSyncTime
}