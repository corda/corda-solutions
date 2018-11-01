package net.corda.businessnetworks.ticketing.contracts

import net.corda.businessnetworks.membership.states.MembershipState
import net.corda.businessnetworks.ticketing.flows.bno.ExpireTicketFromSchedulerFlow
import net.corda.core.contracts.*
import net.corda.core.contracts.Requirements.using
import net.corda.core.flows.FlowLogicRefFactory
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import java.time.Instant

class Ticket : Contract {
    companion object {
        val CONTRACT_NAME = "net.corda.businessnetworks.ticketing.contracts.Ticket"
    }

    open class Commands : CommandData, TypeOnlyCommandData() {
        class Request : Commands()
        class Revoke : Commands()
        class Activate : Commands()
    }

    override fun verify(tx : LedgerTransaction) {

        val command = tx.commands.requireSingleCommand<Commands>()

        fun verifyRequest() {
            val outputState = tx.outputs.single { it.data is Ticket.State<*> }.data as Ticket.State<*>

            "There must be no input states in this transaction" using (tx.inputStates.isEmpty())
            "The BNO has to be among the signers of the request" using (command.signers.contains(outputState.bno.owningKey))
            "The proposed holder has to be among the signers of the request" using (command.signers.contains(outputState.holder.owningKey))
            "Output state of a ticket activation transaction must be pending" using (outputState.isPending())

        }

        fun verifyActivate() {
            val inputState = tx.inputStates.single {it is Ticket.State<*> } as Ticket.State<*>
            val outputState = tx.outputs.single { it.data is Ticket.State<*> }.data as Ticket.State<*>

            "Input state of a ticket activation transaction must be pending" using (inputState.isPending())
            "Output state of a ticket activation transaction must be active" using (outputState.isActive())
            "The BNO has to be among the signers of the activation" using (command.signers.contains(outputState.bno.owningKey))
            "Input and output states of a ticket activation transaction shouldn't change anything but status" using (inputState::class == outputState::class && inputState.withNewStatus(TicketStatus.ACTIVE) == outputState)

        }

        fun verifyRevoke() {
            val inputState = tx.inputStates.single {it is Ticket.State<*> } as Ticket.State<*>

            "There must be no output states on this transaction" using (tx.outputStates.isEmpty())
            "The BNO has to be among the signers of the revocation" using (command.signers.contains(inputState.bno.owningKey))
        }

        when (command.value) {
            is Commands.Request -> verifyRequest()
            is Commands.Activate -> verifyActivate()
            is Commands.Revoke -> verifyRevoke()
            else -> throw IllegalArgumentException("Unsupported command ${command.value}")
        }

    }


    abstract class State<T>(
                     val holder : Party,
                     val bno : Party,
                     val subject : T,
                     val issued : Instant,
                     val status : TicketStatus,
                     override val linearId : UniqueIdentifier) : LinearState, SchedulableState {
        override val participants = listOf(bno, holder)

        fun isPending() = status == TicketStatus.PENDING
        fun isActive() = status == TicketStatus.ACTIVE

        override fun nextScheduledActivity(thisStateRef: StateRef, flowLogicRefFactory: FlowLogicRefFactory): ScheduledActivity? {
            val expireAt = expireAt()
            return if(expireAt == null) {
                null
            } else {
                val flowRef = flowLogicRefFactory.create(ExpireTicketFromSchedulerFlow::class.java, thisStateRef)
                return ScheduledActivity(flowRef, expireAt)
            }
        }

        abstract fun withNewStatus(newStatus : TicketStatus) : State<T>
        abstract fun doesApplyToMember(membershipState : MembershipState<*>) : Boolean
        abstract fun expireAt() : Instant?

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as State<*>

            if (holder != other.holder) return false
            if (bno != other.bno) return false
            if (subject != other.subject) return false
            if (issued != other.issued) return false
            if (status != other.status) return false
            if (linearId != other.linearId) return false
            if (participants != other.participants) return false

            return true
        }

        override fun hashCode(): Int {
            var result = holder.hashCode()
            result = 31 * result + bno.hashCode()
            result = 31 * result + (subject?.hashCode() ?: 0)
            result = 31 * result + issued.hashCode()
            result = 31 * result + status.hashCode()
            result = 31 * result + linearId.hashCode()
            result = 31 * result + participants.hashCode()
            return result
        }


    }

    open class PartiesTargetedTicket<T>(holder : Party,
                                   bno : Party,
                                   subject : T,
                                   val appliesTo : List<Party>,
                                   status : TicketStatus = TicketStatus.PENDING,
                                   issued : Instant = Instant.now(),
                                   linearId : UniqueIdentifier = UniqueIdentifier()) : State<T>(holder, bno, subject, issued, status, linearId) {

        override fun withNewStatus(newStatus : TicketStatus) : State<T> {
            return PartiesTargetedTicket(holder, bno, subject, appliesTo, newStatus, issued, linearId)
        }

        override fun doesApplyToMember(membershipState: MembershipState<*>) : Boolean {
            return appliesTo.contains(membershipState.member)
        }

        override fun expireAt(): Instant? {
            return null
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            if (!super.equals(other)) return false

            other as PartiesTargetedTicket<*>

            if (appliesTo != other.appliesTo) return false

            return true
        }

        override fun hashCode(): Int {
            var result = super.hashCode()
            result = 31 * result + appliesTo.hashCode()
            return result
        }


    }

    open class WideTicket<T>(holder : Party,
                     bno : Party,
                     subject : T,
                     status : TicketStatus = TicketStatus.PENDING,
                     issued : Instant = Instant.now(),
                     linearId : UniqueIdentifier = UniqueIdentifier()) : State<T>(holder, bno, subject, issued, status, linearId) {

        override fun withNewStatus(newStatus : TicketStatus) : State<T> {
            return WideTicket(holder, bno, subject, newStatus, issued, linearId)
        }

        override fun doesApplyToMember(membershipState: MembershipState<*>): Boolean {
            return true //it's a wide ticket, it applies to all
        }

        override fun expireAt(): Instant? {
            return null
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            if (!super.equals(other)) return false
            return true
        }

    }

    class ExpiringWideTicket<T>(holder : Party,
                        bno : Party,
                        subject : T,
                        val expires : Instant,
                        status : TicketStatus = TicketStatus.PENDING,
                        issued : Instant = Instant.now(),
                        linearId : UniqueIdentifier = UniqueIdentifier()) : WideTicket<T>(holder, bno, subject, status, issued, linearId) {

        override fun expireAt(): Instant? {
            return expires
        }

        override fun withNewStatus(newStatus: TicketStatus): State<T> {
            return ExpiringWideTicket(holder, bno, subject, expires, newStatus, issued, linearId)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            if (!super.equals(other)) return false

            other as ExpiringWideTicket<*>

            if (expires != other.expires) return false

            return true
        }

        override fun hashCode(): Int {
            var result = super.hashCode()
            result = 31 * result + expires.hashCode()
            return result
        }


    }
}

@CordaSerializable
enum class TicketStatus {
    PENDING, ACTIVE
}