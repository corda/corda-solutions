package com.r3.businessnetworks.membership.member

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.commons.SupportReceiveFinalityFlow
import com.r3.businessnetworks.membership.member.service.MemberConfigurationService
import com.r3.businessnetworks.membership.member.support.BusinessNetworkAwareInitiatingFlow
import com.r3.businessnetworks.membership.states.MembershipContract
import com.r3.businessnetworks.membership.states.MembershipState
import net.corda.core.flows.FlowException
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction

@CordaSerializable
data class AmendMembershipMetadataRequest(val metadata : Any)

/**
 * Proposes a change to the membership metadata
 */
@InitiatingFlow
class AmendMembershipMetadataFlow(bno : Party, private val newMetadata : Any) : BusinessNetworkAwareInitiatingFlow<SignedTransaction>(bno) {

    @Suspendable
    override fun afterBNOIdentityVerified() : SignedTransaction {
        val bnoSession = initiateFlow(bno)
        bnoSession.send(AmendMembershipMetadataRequest(newMetadata))

        val signTransactionFlow = object : SignTransactionFlow(bnoSession) {
            override fun checkTransaction(stx : SignedTransaction) {
                val configuration = serviceHub.cordaService(MemberConfigurationService::class.java)

                val newMembership = stx.coreTransaction.outputs.single()
                val newMembershipState = newMembership.data as MembershipState<*>

                if (newMembership.contract != configuration.membershipContractName()) {
                    throw FlowException("Membership transactions have to be verified with ${configuration.membershipContractName()} contract")
                }

                if (stx.tx.commands.single().value !is MembershipContract.Commands.Amend) {
                    throw FlowException("Invalid command.")
                }

                if (newMembershipState.member != ourIdentity) {
                    throw FlowException("Invalid membership state. Wrong member's identity ${newMembershipState.member}.")
                }

                if (newMembershipState.bno != bno) {
                    throw FlowException("Invalid membership state. Wrong BNO's identity ${newMembershipState.bno}.")
                }

                stx.toLedgerTransaction(serviceHub, false).verify()
            }
        }
        val selfSignedTx = subFlow(signTransactionFlow)
        return subFlow(SupportReceiveFinalityFlow(bnoSession, selfSignedTx.id)) ?: selfSignedTx
    }
}