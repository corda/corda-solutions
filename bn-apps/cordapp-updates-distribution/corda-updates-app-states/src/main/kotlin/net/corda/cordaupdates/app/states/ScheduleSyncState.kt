package net.corda.cordaupdates.app.states

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

class ScheduledSyncContract : Contract {
    companion object {
        const val CONTRACT_NAME = "net.corda.cordaupdates.app.states.ScheduleSyncContract"
    }

    interface Commands : CommandData {
        class Start : Commands, TypeOnlyCommandData()
        class Stop : Commands, TypeOnlyCommandData()
    }

    override fun verify(tx : LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        when (command.value) {
            is Commands.Start -> requireThat {
                "There should be no inputs" using (tx.inputs.isEmpty())
                "There should be one output" using (tx.outputs.size == 1)
                val scheduledState = tx.outputStates.single() as ScheduledSyncState
                "Sync interval should be positive" using (scheduledState.syncInterval > 0)
                "Owner should be a participant" using (scheduledState.participants.single() == scheduledState.owner)
            }
            is Commands.Stop -> requireThat {
                "There should be one input" using (tx.inputs.size == 1)
                "There should be no outputs" using (tx.outputs.isEmpty())
            }
            else -> throw IllegalArgumentException("Unsupported command ${command.value}")
        }
    }

}

data class ScheduledSyncState(val syncInterval : Long, val owner : Party) : SchedulableState {
    override val participants = listOf(owner)

    override fun nextScheduledActivity(thisStateRef : StateRef, flowLogicRefFactory : FlowLogicRefFactory) : ScheduledActivity? {
        val now = Instant.now().plusMillis(syncInterval)
        return ScheduledActivity(flowLogicRefFactory.create("net.corda.cordaupdates.app.SyncWithRemoteRepositoryFlow"), Instant.now().plusMillis(syncInterval))
    }
}