package net.corda.cordaupdates.app

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.cordaupdates.core.RepositorySyncer
import net.corda.businessnetworks.cordaupdates.core.SyncerConfiguration
import net.corda.cordaupdates.app.states.ScheduledSyncContract
import net.corda.cordaupdates.app.states.ScheduledSyncState
import net.corda.cordaupdates.transport.flows.ConfigurationProperties.APP_SERVICE_HUB
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.SchedulableFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.queryBy
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.TransactionBuilder
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@SchedulableFlow
class SyncWithRemoteRepositoryFlow (private val syncerConfig : SyncerConfiguration? = null) : FlowLogic<Unit>() {
    constructor() : this(null)

    @Suspendable
    override fun call() {
        val syncerService = serviceHub.cordaService(SyncerService::class.java)
        syncerService.launchAsync(syncerConfig) { }
    }
}

@StartableByRPC
class ScheduleSyncFlow @JvmOverloads constructor(private val syncerConfig : SyncerConfiguration? = null) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
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
            val configuration = serviceHub.cordaService(ClientConfiguration::class.java)
            // reissue state if the sync interval has changed
            if (configuration.syncInterval() != scheduledState.syncInterval) {
                spendScheduledState(scheduledStateAndRef)
                issueScheduledState()
            }
        }

        // launch sync flow
        subFlow(SyncWithRemoteRepositoryFlow(syncerConfig))
    }

    @Suspendable
    private fun spendScheduledState(state : StateAndRef<ScheduledSyncState>) {
        val configuration = serviceHub.cordaService(ClientConfiguration::class.java)
        val notary  = configuration.notaryParty()
        val builder = TransactionBuilder(notary)
                .addInputState(state)
                .addCommand(ScheduledSyncContract.Commands.Stop(), ourIdentity.owningKey)
        builder.verify(serviceHub)
        val signedTx = serviceHub.signInitialTransaction(builder)
        subFlow(FinalityFlow(signedTx))
    }

    @Suspendable
    private fun issueScheduledState() {
        val configuration = serviceHub.cordaService(ClientConfiguration::class.java)
        val notary  = configuration.notaryParty()
        val builder = TransactionBuilder(notary)
                .addOutputState(ScheduledSyncState(configuration.syncInterval(), ourIdentity), ScheduledSyncContract.CONTRACT_NAME)
                .addCommand(ScheduledSyncContract.Commands.Start(), ourIdentity.owningKey)

        builder.verify(serviceHub)
        val signedTx = serviceHub.signInitialTransaction(builder)
        subFlow(FinalityFlow(signedTx))
    }

}

@CordaService
class SyncerService(private val appServiceHub : AppServiceHub) : SingletonSerializeAsToken() {
    companion object {
        val executor = Executors.newSingleThreadExecutor()!!
    }

    fun launchAsync(syncerConfiguration : SyncerConfiguration? = null,
                    onComplete : () -> Unit = {}) {


        val syncer = RepositorySyncer(syncerConfiguration ?: Utils.syncerConfiguration(appServiceHub))

        executor.submit(Callable {
            syncer.sync(mapOf(Pair(APP_SERVICE_HUB, appServiceHub)))
            onComplete()
        })
    }
}