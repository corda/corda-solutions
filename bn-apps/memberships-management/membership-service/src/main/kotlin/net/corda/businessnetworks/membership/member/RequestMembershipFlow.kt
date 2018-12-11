package net.corda.businessnetworks.membership.member

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.member.service.MemberConfigurationService
import net.corda.businessnetworks.membership.member.support.BusinessNetworkAwareInitiatingFlow
import net.corda.businessnetworks.membership.states.MembershipContract
import net.corda.businessnetworks.membership.states.MembershipState
import net.corda.core.flows.FlowException
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker

@CordaSerializable
data class OnBoardingRequest(val metadata : Any)

/**
 * The flow requests BNO to kick-off the on-boarding procedure.
 */
@StartableByRPC
@InitiatingFlow
class RequestMembershipFlow(bno : Party, private val membershipMetadata : Any) : BusinessNetworkAwareInitiatingFlow<SignedTransaction>(bno) {
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
    override fun afterBNOIdentityVerified() : SignedTransaction {
        progressTracker.currentStep = SENDING_MEMBERSHIP_DATA_TO_BNO

        val bnoSession = initiateFlow(bno)
        bnoSession.send(OnBoardingRequest(membershipMetadata))

        val signResponder = object : SignTransactionFlow(bnoSession) {
            override fun checkTransaction(stx : SignedTransaction) {
                val configuration = serviceHub.cordaService(MemberConfigurationService::class.java)

                val command = stx.tx.commands.single()
                if (command.value !is MembershipContract.Commands.Request) {
                    throw FlowException("Only Request command is allowed")
                }

                val output = stx.tx.outputs.single()
                if (output.contract != configuration.membershipContractName()) {
                    throw FlowException("Membership transactions have to be verified with ${configuration.membershipContractName()} contract")
                }

                val membershipState = output.data as MembershipState<*>
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
        subFlow(signResponder)
        return subFlow(ReceiveFinalityFlow(bnoSession))
    }
}