package com.r3.businessnetworks.billing.flows.bno.responders

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.billing.flows.member.ReturnBillingStateFlow
import com.r3.businessnetworks.billing.states.BillingContract
import com.r3.businessnetworks.billing.states.BillingState
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.transactions.SignedTransaction

@InitiatedBy(ReturnBillingStateFlow::class)
open class ReturnBillingStateFlowResponder(private val session : FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val stx = subFlow(object : SignTransactionFlow(session) {
            override fun checkTransaction(stx : SignedTransaction) = verifyTransaction(stx)
        })
        // nothing to verify here
        subFlow(ReceiveFinalityFlow(session, stx.id))
    }


    protected open fun verifyTransaction(stx : SignedTransaction) {
        val ledgerTx = stx.toLedgerTransaction(serviceHub, checkSufficientSignatures = false)
        ledgerTx.verify()
        val command = stx.tx.commands.single()
        if (command.value !is BillingContract.Commands.Return) throw FlowException("Wrong command")

        val inputBillingState = ledgerTx.inputStates.single() as BillingState
        if (inputBillingState.issuer != ourIdentity) throw FlowException("Wrong issuer")
    }
}