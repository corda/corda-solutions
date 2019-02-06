package com.r3.businessnetworks.billing.flows.bno.responders

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.billing.flows.member.AttachUnspentChipsFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.ReceiveFinalityFlow

@InitiatedBy(AttachUnspentChipsFlow::class)
class AttachUnspentChipsResponder(private val session: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(ReceiveFinalityFlow(session))
    }
}