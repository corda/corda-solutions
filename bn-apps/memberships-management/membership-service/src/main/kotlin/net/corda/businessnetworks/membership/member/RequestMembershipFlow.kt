package net.corda.businessnetworks.membership.member

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.member.service.MemberConfigurationService
import net.corda.businessnetworks.membership.states.MembershipContract
import net.corda.businessnetworks.membership.states.MembershipState
import net.corda.core.flows.*
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker

@CordaSerializable
data class OnBoardingRequest<out T>(val metadata : T)

/**
 * The flow requests BNO to kick-off the on-boarding procedure
 */
@StartableByRPC
@InitiatingFlow
class RequestMembershipFlow<T>(private val membershipMetadata : T) : FlowLogic<SignedTransaction>() {

    companion object {
        object SENDING_MEMBERSHIP_DATA_TO_BNO : ProgressTracker.Step("Sending membership data to BNO")
        object ACCEPTING_INCOMING_PENDING_MEMBERSHIP : ProgressTracker.Step("Accepting incoming pending membership")

        fun tracker() = ProgressTracker(
                SENDING_MEMBERSHIP_DATA_TO_BNO,
                ACCEPTING_INCOMING_PENDING_MEMBERSHIP
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call() : SignedTransaction {
        progressTracker.currentStep = SENDING_MEMBERSHIP_DATA_TO_BNO
        val configuration = serviceHub.cordaService(MemberConfigurationService::class.java)
        val bno = configuration.bnoParty()

        val bnoSession = initiateFlow(bno)
        bnoSession.send(OnBoardingRequest(membershipMetadata))

        val signResponder = object : SignTransactionFlow(bnoSession) {
            override fun checkTransaction(stx : SignedTransaction) {
                val command = stx.tx.commands.single()
                if (command.value !is MembershipContract.Commands.Request) {
                    throw FlowException("Only Request command is allowed")
                }

                val output = stx.tx.outputs.single()
                if (output.contract != MembershipContract.CONTRACT_NAME) {
                    throw FlowException("Output state has to be verified by ${MembershipContract.CONTRACT_NAME}")
                }
                val membershipState = output.data as MembershipState<T>
                if (bno != membershipState.bno) {
                    throw IllegalArgumentException("Wrong BNO identity")
                }
                if (ourIdentity != membershipState.member) {
                    throw IllegalArgumentException("We have to be the member")
                }

                stx.toLedgerTransaction(serviceHub, false).verify()
            }
        }
        progressTracker.currentStep = ACCEPTING_INCOMING_PENDING_MEMBERSHIP
        return subFlow(signResponder)
    }
}

