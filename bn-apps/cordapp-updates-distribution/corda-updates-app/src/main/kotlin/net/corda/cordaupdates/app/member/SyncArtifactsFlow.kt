package net.corda.cordaupdates.app.member

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.cordaupdates.core.ArtifactMetadata
import net.corda.businessnetworks.cordaupdates.core.SyncerConfiguration
import net.corda.cordaupdates.app.states.ScheduledSyncContract
import net.corda.cordaupdates.app.states.ScheduledSyncState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.SchedulableFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

/**
 * Downloads missing artifacts versions from the configured remote repositories.
 *
 * @param scheduledStateRef reference to the schedule state. Automatically provided if the flow is started by Corda scheduler.
 * @param syncerConfig configuration for [CordappSyncer]. If not provided - will be red from a file, defined in the cordapp configuration.
 * @param launchAsync flag that indicates whether the synchronisation should be started asynchronously.
 *      Synchronous invocations shouldn't be used for Corda-based transports.
 *
 */
@SchedulableFlow
@StartableByRPC
class SyncArtifactsFlow private constructor (private val scheduledStateRef : StateRef? = null,
                         private val syncerConfig : SyncerConfiguration? = null,
                         private val launchAsync : Boolean = true) : FlowLogic<List<ArtifactMetadata>?>() {
    // this constructor is needed when the flow is invoked by Corda scheduler
    constructor(scheduledStateRef : StateRef) : this(scheduledStateRef, null, true)
    constructor(syncerConfig : SyncerConfiguration? = null,
                launchAsync : Boolean = true) : this(null, syncerConfig, launchAsync)

    // TODO: remove the progress tracker once Corda v4 is released
    companion object {
        object LAUNCHING_SYNCHRONISATION : ProgressTracker.Step("Invoking initial synchronisation")

        fun tracker() = ProgressTracker(
                LAUNCHING_SYNCHRONISATION
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call() : List<ArtifactMetadata>? {
        val syncerService = serviceHub.cordaService(SyncerService::class.java)
        progressTracker.currentStep = LAUNCHING_SYNCHRONISATION
        return if (launchAsync) {
            syncerService.syncArtifactsAsync(syncerConfig)
            null
        } else {
            syncerService.syncArtifacts(syncerConfig)
        }
    }
}

/**
 * Issues a [ScheduledSyncState] onto the ledger (all existing [ScheduledSyncState]s will be spent first) and then triggers
 * a synchronisation via [SyncArtifactsFlow]
 */
@StartableByRPC
class ScheduleSyncFlow @JvmOverloads constructor(private val syncerConfig : SyncerConfiguration? = null,
                                                 private val launchAsync : Boolean = true) : FlowLogic<List<ArtifactMetadata>?>() {

    // TODO: remove the progress tracker once Corda v4 is released
    companion object {
        object SCHEDULING_STATE : ProgressTracker.Step("Scheduling state")
        object INVOKING_INITIAL_SYNCRONISATION : ProgressTracker.Step("Invoking initial synchronisation") {
            override fun childProgressTracker() = SyncArtifactsFlow.tracker()
        }

        fun tracker() = ProgressTracker(
                SCHEDULING_STATE,
                INVOKING_INITIAL_SYNCRONISATION
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call() : List<ArtifactMetadata>? {
        progressTracker.currentStep = SCHEDULING_STATE
        val configuration = serviceHub.cordaService(MemberConfiguration::class.java)

        val scheduledStates = serviceHub.vaultService.queryBy<ScheduledSyncState>().states

        // spending all existing scheduled states
        scheduledStates.forEach { spendScheduledState(it, configuration.notaryParty()) }

        // issuing a new scheduled state
        issueScheduledState(configuration.syncInterval(), configuration.notaryParty())

        progressTracker.currentStep = INVOKING_INITIAL_SYNCRONISATION
        // triggering first sync
        return subFlow(SyncArtifactsFlow(syncerConfig, launchAsync))
    }

    @Suspendable
    private fun spendScheduledState(state : StateAndRef<ScheduledSyncState>, notary : Party) {
        val builder = TransactionBuilder(notary)
                .addInputState(state)
                .addCommand(ScheduledSyncContract.Commands.Stop(), ourIdentity.owningKey)
        builder.verify(serviceHub)
        val signedTx = serviceHub.signInitialTransaction(builder)
        subFlow(FinalityFlow(signedTx))
    }

    @Suspendable
    private fun issueScheduledState(syncInterval : Long, notary : Party) : SignedTransaction {
        val builder = TransactionBuilder(notary)
                .addOutputState(ScheduledSyncState(syncInterval, ourIdentity), ScheduledSyncContract.CONTRACT_NAME)
                .addCommand(ScheduledSyncContract.Commands.Start(), ourIdentity.owningKey)

        builder.verify(serviceHub)
        val signedTx = serviceHub.signInitialTransaction(builder)
        return subFlow(FinalityFlow(signedTx))
    }
}