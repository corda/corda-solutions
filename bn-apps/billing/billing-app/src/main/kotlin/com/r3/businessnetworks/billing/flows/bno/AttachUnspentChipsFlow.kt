package com.r3.businessnetworks.billing.flows.bno

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party

@InitiatingFlow
class AttachUnspentChipsFlow(val party : Party) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val session = initiateFlow(party)
        session.sendAndReceive<String>("Attach")
    }
}