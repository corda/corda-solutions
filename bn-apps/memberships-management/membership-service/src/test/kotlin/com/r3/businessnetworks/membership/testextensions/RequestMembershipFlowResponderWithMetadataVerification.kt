package com.r3.businessnetworks.membership.testextensions

import com.r3.businessnetworks.membership.flows.bno.RequestMembershipFlowResponder
import com.r3.businessnetworks.membership.flows.member.RequestMembershipFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.transactions.TransactionBuilder
import java.lang.IllegalArgumentException

@InitiatedBy(RequestMembershipFlow::class)
class RequestMembershipFlowResponderWithMetadataVerification(session : FlowSession) : RequestMembershipFlowResponder(session) {
    override fun verifyTransaction(builder : TransactionBuilder) {
        throw FlowException("Invalid metadata")
    }
}