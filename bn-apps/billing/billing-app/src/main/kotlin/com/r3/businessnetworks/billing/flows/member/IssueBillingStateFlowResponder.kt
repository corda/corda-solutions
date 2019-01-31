package com.r3.businessnetworks.billing.flows.member

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.billing.flows.bno.IssueBillingStateFlow
import com.r3.businessnetworks.billing.flows.member.service.MemberConfigurationService
import com.r3.businessnetworks.billing.states.BillingContract
import com.r3.businessnetworks.billing.states.BillingState
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.transactions.SignedTransaction

/**
 * Responder to the [IssueBillingStateFlow]
 */
@InitiatedBy(IssueBillingStateFlow::class)
class IssueBillingStateFlowResponder(private val session : FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val signResponder = object : SignTransactionFlow(session) {
            @Suspendable
            override fun checkTransaction(stx : SignedTransaction) {
                val configuration = serviceHub.cordaService(MemberConfigurationService::class.java)
                val billingState = stx.tx.outputStates.single() as BillingState
                if (billingState.owner != ourIdentity) throw FlowException("Wrong owner")
                if (billingState.issuer != configuration.bnoParty()) throw FlowException("Wrong issuer")
                if (stx.notary != configuration.notaryParty()) throw FlowException("Wrong notary")
                if (stx.tx.commands.single().value !is BillingContract.Commands.Issue) throw FlowException("Wrong command")
            }
        }
        val stx = subFlow(signResponder)
        subFlow(ReceiveFinalityFlow(session, stx.id))
    }
}