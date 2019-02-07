package com.r3.businessnetworks.billing.flows.member.responders

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.billing.flows.bno.RequestReturnOfBillingStateFlow
import com.r3.businessnetworks.billing.flows.bno.ReturnRequest
import com.r3.businessnetworks.billing.flows.member.AttachUnspentChipsAndReturnBillingStateFlow
import com.r3.businessnetworks.billing.flows.member.service.MemberDatabaseService
import com.r3.businessnetworks.billing.states.BillingStateStatus
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.utilities.unwrap

/**
 * Responder to [RequestReturnOfBillingStateFlow].
 * Verifies received return request, attaches all unspent [BillingChipState]s and returns the [BillingState] to the issuer.
 */
@InitiatedBy(RequestReturnOfBillingStateFlow::class)
class RequestReturnOfBillingStateFlowResponder(private val session : FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val billingState = session.receive<ReturnRequest>().unwrap {
            val databaseService = serviceHub.cordaService(MemberDatabaseService::class.java)
            val billingState = databaseService.getBillingStateByLinearId(it.billingStateLinearId)
            if (billingState == null || billingState.state.data.issuer != session.counterparty)
                throw FlowException("No BillingState has been found for linearId=${it.billingStateLinearId} and issuer=${session.counterparty}")
            if (billingState.state.data.status != BillingStateStatus.ACTIVE)
                throw FlowException("Only active states can be returned")
            billingState
        }!!
        subFlow(AttachUnspentChipsAndReturnBillingStateFlow(billingState))
    }
}