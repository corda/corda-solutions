package net.corda.businessnetworks.ticketing.contracts

import net.corda.core.contracts.*
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

    }


    abstract class State<T>(
                     val holder : Party,
                     val bno : Party,
                     val subject : T,
                     val issued : Instant = Instant.now(),
                     val status : TicketStatus = TicketStatus.PENDING,
                     override val linearId : UniqueIdentifier = UniqueIdentifier()) : LinearState {
        override val participants = listOf(bno, holder)

        fun isRevoked() = status == TicketStatus.REVOKED
        fun isPending() = status == TicketStatus.PENDING
        fun isActive() = status == TicketStatus.ACTIVE

        abstract fun withNewStatus(newStatus : TicketStatus) : State<T>
    }

    class TargetedTicket<T>(holder : Party,
                         bno : Party,
                         subject : T,
                         val appliesTo : List<Party>,
                         status : TicketStatus = TicketStatus.PENDING) : State<T>(holder, bno, subject, status = status) {

        override fun withNewStatus(newStatus : TicketStatus) : State<T> {
            return TargetedTicket(holder, bno, subject, appliesTo, newStatus)
        }

    }

    class WideTicket<T>(holder : Party,
                     bno : Party,
                     subject : T,
                     status : TicketStatus = TicketStatus.PENDING) : State<T>(holder, bno, subject, status = status) {

        override fun withNewStatus(newStatus : TicketStatus) : State<T> {
            return WideTicket(holder, bno, subject, newStatus)
        }
    }
}

@CordaSerializable
enum class TicketStatus {
    PENDING, ACTIVE, REVOKED
}