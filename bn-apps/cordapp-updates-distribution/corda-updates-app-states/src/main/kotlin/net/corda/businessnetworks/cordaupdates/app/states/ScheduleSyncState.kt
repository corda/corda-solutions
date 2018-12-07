package net.corda.businessnetworks.cordaupdates.app.states

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.SchedulableState
import net.corda.core.contracts.ScheduledActivity
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.flows.FlowLogicRefFactory
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import java.lang.IllegalArgumentException
import java.time.Instant

/**
 * Simple state and contract to schedule periodic CorDapp synchronisation
 *
 * TODO: should be updated to use a proper scheduling once this feature is implemented: https://r3-cev.atlassian.net/browse/CORDA-2117
 */
class ScheduledSyncContract : Contract {
    companion object {
        const val CONTRACT_NAME = "net.corda.businessnetworks.cordaupdates.app.states.ScheduledSyncContract"
    }

    interface Commands : CommandData {
        class Start : Commands, TypeOnlyCommandData()
        class Stop : Commands, TypeOnlyCommandData()
    }

    override fun verify(tx : LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        when (command.value) {
            is Commands.Start -> requireThat {
                "There should be no inputs" using (tx.inputsOfType<ScheduledSyncState>().isEmpty())
                "There should be one output" using (tx.outputsOfType<ScheduledSyncState>().size == 1)
                val scheduledState = tx.outputsOfType<ScheduledSyncState>().single()
                "Sync interval should be positive" using (scheduledState.syncInterval > 0)
                "Owner should be a participant" using (scheduledState.participants.single() == scheduledState.owner)
            }
            is Commands.Stop -> requireThat {
                "There should be one input" using (tx.inputsOfType<ScheduledSyncState>().size == 1)
                "There should be no outputs" using (tx.outputsOfType<ScheduledSyncState>().isEmpty())

            }
            else -> throw IllegalArgumentException("Unsupported command ${command.value}")
        }
    }
}

data class ScheduledSyncState(val syncInterval : Long,
                              val owner : Party) : SchedulableState {
    override val participants = listOf(owner)

    override fun nextScheduledActivity(thisStateRef : StateRef, flowLogicRefFactory : FlowLogicRefFactory)
            = ScheduledActivity(flowLogicRefFactory.create("net.corda.businessnetworks.cordaupdates.app.member.SyncArtifactsFlow", thisStateRef), Instant.now().plusMillis(syncInterval))
}