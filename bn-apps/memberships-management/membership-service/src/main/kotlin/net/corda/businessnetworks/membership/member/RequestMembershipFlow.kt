package net.corda.businessnetworks.membership.member

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.member.service.MemberConfigurationService
import net.corda.businessnetworks.membership.states.MembershipMetadata
import net.corda.businessnetworks.membership.states.Membership
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction

@CordaSerializable
data class OnBoardingRequest(val metadata : MembershipMetadata)

/**
 * The flow requests BNO to kick-off the on-boarding procedure
 */
@InitiatingFlow
class RequestMembershipFlow(private val membershipMetadata : MembershipMetadata = MembershipMetadata("DEFAULT")) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call() : SignedTransaction {
        val configuration = serviceHub.cordaService(MemberConfigurationService::class.java)
        val bno = configuration.bnoParty()

        val bnoSession = initiateFlow(bno)
        bnoSession.send(OnBoardingRequest(membershipMetadata))

        val signResponder = object : SignTransactionFlow(bnoSession) {
            override fun checkTransaction(stx : SignedTransaction) {
                val command = stx.tx.commands.single()
                if (command.value !is Membership.Commands.Request) {
                    throw FlowException("Only Request command is allowed")
                }

                val output = stx.tx.outputs.single()
                if (output.contract != Membership.CONTRACT_NAME) {
                    throw FlowException("Output state has to be verified by ${Membership.CONTRACT_NAME}")
                }
                val membershipState = output.data as Membership.State
                if (bno != membershipState.bno) {
                    throw IllegalArgumentException("Wrong BNO identity")
                }
                if (ourIdentity != membershipState.member) {
                    throw IllegalArgumentException("We have to be the member")
                }

                stx.toLedgerTransaction(serviceHub, false).verify()
            }
        }
        return subFlow(signResponder)
    }
}

