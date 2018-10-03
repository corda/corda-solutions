package net.corda.cordaupdates.app.member

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.cordaupdates.core.ArtifactMetadata
import net.corda.businessnetworks.cordaupdates.core.SyncerConfiguration
import net.corda.cordaupdates.app.states.ScheduledSyncContract
import net.corda.cordaupdates.app.states.ScheduledSyncState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.SchedulableFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

@SchedulableFlow
@StartableByRPC
class SyncArtifactsFlow (private val syncerConfig : SyncerConfiguration? = null, private val launchAsync : Boolean = true) : FlowLogic<List<ArtifactMetadata>?>() {
    constructor() : this(null, true)

    @Suspendable
    override fun call() : List<ArtifactMetadata>? {
        val syncerService = serviceHub.cordaService(SyncerService::class.java)
        return if (launchAsync) {
            syncerService.syncArtifactsAsync(syncerConfig)
            null
        } else {
            syncerService.syncArtifacts(syncerConfig)
        }
    }
}

@StartableByRPC
class ScheduleSyncFlow @JvmOverloads constructor(private val syncerConfig : SyncerConfiguration? = null, private val launchAsync : Boolean = true) : FlowLogic<List<ArtifactMetadata>?>() {

    @Suspendable
    override fun call() : List<ArtifactMetadata>?? {
        val scheduledStates = serviceHub.vaultService.queryBy<ScheduledSyncState>().states

        // There should never be more than one sync schedule state stored in the vault
        // If there is more than one state exist in the vault - we spend all the states and issues a new one
        if (scheduledStates.size > 1) {
            scheduledStates.forEach { spendScheduledState(it) }
            // issuing a new state
            issueScheduledState()
        } else if (scheduledStates.isEmpty()) {
            issueScheduledState()
        } else {
            val scheduledStateAndRef = scheduledStates.single()
            val scheduledState = scheduledStateAndRef.state.data
            val configuration = serviceHub.cordaService(MemberConfiguration::class.java)
            // reissue state if the sync interval has changed
            if (configuration.syncInterval() != scheduledState.syncInterval) {
                spendScheduledState(scheduledStateAndRef)
                issueScheduledState()
            }
        }

        // syncArtifacts sync flow
        return subFlow(SyncArtifactsFlow(syncerConfig, launchAsync))
    }

    @Suspendable
    private fun spendScheduledState(state : StateAndRef<ScheduledSyncState>) {
        val configuration = serviceHub.cordaService(MemberConfiguration::class.java)
        val notary = configuration.notaryParty()
        val builder = TransactionBuilder(notary)
                .addInputState(state)
                .addCommand(ScheduledSyncContract.Commands.Stop(), ourIdentity.owningKey)
        builder.verify(serviceHub)
        val signedTx = serviceHub.signInitialTransaction(builder)
        subFlow(FinalityFlow(signedTx))
    }

    @Suspendable
    private fun issueScheduledState() : SignedTransaction {
        val configuration = serviceHub.cordaService(MemberConfiguration::class.java)
        val notary = configuration.notaryParty()
        val builder = TransactionBuilder(notary)
                .addOutputState(ScheduledSyncState(configuration.syncInterval(), ourIdentity), ScheduledSyncContract.CONTRACT_NAME)
                .addCommand(ScheduledSyncContract.Commands.Start(), ourIdentity.owningKey)

        builder.verify(serviceHub)
        val signedTx = serviceHub.signInitialTransaction(builder)
        return subFlow(FinalityFlow(signedTx))
    }
}