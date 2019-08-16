package com.r3.businessnetworks.membership.flows.member

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.flows.member.support.BusinessNetworkAwareInitiatingFlow
import com.r3.businessnetworks.membership.states.MembershipContract
import com.r3.businessnetworks.membership.states.MembershipState
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

@CordaSerializable
data class OnBoardingRequest(val metadata: Any, val networkID: String?)

/**
 * The flow requests BNO to kick-off the on-boarding procedure.
 */
@StartableByRPC
@InitiatingFlow(version = 2)
open class RequestMembershipFlow(bno: Party, private val membershipMetadata: Any, private val joiningNetworkID: String) : BusinessNetworkAwareInitiatingFlow<SignedTransaction>(bno) {
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
    override fun afterBNOIdentityVerified(): SignedTransaction {
        progressTracker.currentStep = SENDING_MEMBERSHIP_DATA_TO_BNO

        val bnoSession = initiateFlow(bno)
        bnoSession.send(OnBoardingRequest(membershipMetadata, joiningNetworkID))
        val signResponder = object : SignTransactionFlow(bnoSession) {
            override fun checkTransaction(stx: SignedTransaction) {
                val command = stx.tx.commands.single()
                if (command.value !is MembershipContract.Commands.Request) {
                    throw FlowException("Only Request command is allowed")
                }

                val output = stx.tx.outputs.single()

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

        if (ourIdentity != bno) {
            val selfSignedTx = subFlow(signResponder)

            return if (bnoSession.getCounterpartyFlowInfo().flowVersion != 1) {
                subFlow(ReceiveFinalityFlow(bnoSession, selfSignedTx.id))
            } else {
                selfSignedTx
            }
        } else {
            return bnoSession.receive<SignedTransaction>().unwrap { it }
        }
    }
}
