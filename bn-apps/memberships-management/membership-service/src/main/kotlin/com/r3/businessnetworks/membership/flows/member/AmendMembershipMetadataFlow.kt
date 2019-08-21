package com.r3.businessnetworks.membership.flows.member

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.flows.member.support.BusinessNetworkAwareInitiatingFlow
import com.r3.businessnetworks.membership.states.MembershipContract
import com.r3.businessnetworks.membership.states.MembershipState
import net.corda.core.flows.FlowException
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction

@CordaSerializable
data class AmendMembershipMetadataRequest(val metadata : Any, val networkID: String?)

/**
 * Proposes a change to the membership metadata
 */
@InitiatingFlow(version = 2)
open class AmendMembershipMetadataFlow(bno : Party, private val newMetadata : Any, private val networkID:String?) : BusinessNetworkAwareInitiatingFlow<SignedTransaction>(bno) {

    @Suspendable
    override fun afterBNOIdentityVerified() : SignedTransaction {
        val bnoSession = initiateFlow(bno)
        bnoSession.send(AmendMembershipMetadataRequest(newMetadata, networkID))

        val signTransactionFlow = object : SignTransactionFlow(bnoSession) {
            override fun checkTransaction(stx : SignedTransaction) {
                val newMembership = stx.coreTransaction.outputs.single()
                val newMembershipState = newMembership.data as MembershipState<*>

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

        return if (bnoSession.getCounterpartyFlowInfo().flowVersion == 1) {
            selfSignedTx
        } else {
            subFlow(ReceiveFinalityFlow(bnoSession, selfSignedTx.id))
        }
    }
}