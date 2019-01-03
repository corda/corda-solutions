package net.corda.businessnetworks.membership.member

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.member.service.MemberConfigurationService
import net.corda.businessnetworks.membership.states.MembershipMetadata
import net.corda.businessnetworks.membership.states.Membership
import net.corda.core.flows.*
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction

@CordaSerializable
data class AmendMembershipMetadataRequest(val metadata : MembershipMetadata)

/**
 * Proposes a change to the membership metadata
 */
@InitiatingFlow
class AmendMembershipMetadataFlow(private val newMetadata : MembershipMetadata) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call() : SignedTransaction {
        val configuration = serviceHub.cordaService(MemberConfigurationService::class.java)
        val bno = configuration.bnoParty()

        val bnoSession = initiateFlow(bno)
        bnoSession.send(AmendMembershipMetadataRequest(newMetadata))

        val signTransactionFlow = object : SignTransactionFlow(bnoSession) {
            override fun checkTransaction(stx : SignedTransaction) {
                val newMembership = stx.coreTransaction.outputs.single()
                val newMembershipState = newMembership.data as Membership.State

                if (stx.tx.commands.single().value !is Membership.Commands.Amend) {
                    throw FlowException("Invalid command.")
                }

                if (newMembershipState.member != ourIdentity) {
                    throw FlowException("Invalid membership state. Wrong member's identity ${newMembershipState.member}.")
                }

                if (newMembershipState.bno != bno) {
                    throw FlowException("Invalid membership state. Wrong BNO's identity ${newMembershipState.bno}.")
                }

                if (newMembership.contract != Membership.CONTRACT_NAME) {
                    throw FlowException("Invalid membership state. Wrong contract ${newMembership.contract}")
                }

                stx.toLedgerTransaction(serviceHub, false).verify()
            }
        }
        subFlow(signTransactionFlow)
        return subFlow(ReceiveFinalityFlow(bnoSession))
    }
}

