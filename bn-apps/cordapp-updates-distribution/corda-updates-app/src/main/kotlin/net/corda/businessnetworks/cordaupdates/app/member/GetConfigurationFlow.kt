package net.corda.businessnetworks.cordaupdates.app.member

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.cordaupdates.core.SyncerConfiguration
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.utilities.ProgressTracker

@StartableByRPC
class GetCDSConfigurationFlow : FlowLogic<SyncerConfiguration>() {
    // TODO: remove for Corda v4
    companion object {
        object LOADING_CONFIGURATION : ProgressTracker.Step("Loading configuration")

        fun tracker() = ProgressTracker(
                LOADING_CONFIGURATION
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call() : SyncerConfiguration {
        progressTracker.currentStep = LOADING_CONFIGURATION
        return SyncerService.loadSyncerConfiguration(serviceHub)
    }
}