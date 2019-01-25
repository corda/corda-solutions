package com.r3.businessnetworks.membership.testextensions

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.flows.bno.AmendMembershipMetadataFlowResponder
import com.r3.businessnetworks.membership.flows.member.AmendMembershipMetadataFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.transactions.TransactionBuilder

@InitiatedBy(AmendMembershipMetadataFlow::class)
class AmendMembershipMetadataFlowResponderWithCustomVerification(session : FlowSession) : AmendMembershipMetadataFlowResponder(session) {
    @Suspendable
    override fun verifyTransaction(builder : TransactionBuilder) {
        throw FlowException("Invalid metadata")
    }
}