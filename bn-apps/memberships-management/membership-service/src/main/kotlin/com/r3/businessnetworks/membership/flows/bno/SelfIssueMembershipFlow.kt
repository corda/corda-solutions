package com.r3.businessnetworks.membership.flows.bno

import com.r3.businessnetworks.membership.flows.bno.service.BNOConfigurationService
import com.r3.businessnetworks.membership.flows.member.Utils.throwExceptionIfNotBNO
import com.r3.businessnetworks.membership.states.MembershipContract
import com.r3.businessnetworks.membership.states.MembershipState
import com.r3.businessnetworks.membership.states.MembershipStatus
import net.corda.core.contracts.Command
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

/**
 * Self Issue a Membership.  This can only be done by a BNO
 */
@StartableByRPC
@InitiatingFlow(version = 2)
open class SelfIssueMembershipFlow(val metaData : Any) : FlowLogic<SignedTransaction>() {
    companion object {
        object IssuingMembership : ProgressTracker.Step("Issuing Membership")

        fun tracker() = ProgressTracker(
                IssuingMembership
        )
    }

    override val progressTracker = tracker()

    override fun call(): SignedTransaction {
        throwExceptionIfNotBNO(ourIdentity, serviceHub)
        progressTracker.currentStep = IssuingMembership

        val config = serviceHub.cordaService(BNOConfigurationService::class.java)
        val notary = config.notaryParty()
        val membershipState = MembershipState(this.ourIdentity, this.ourIdentity, metaData, status = MembershipStatus.ACTIVE)
        val txCommand = Command(MembershipContract.Commands.SelfIssue(), ourIdentity.owningKey)

        val txBuilder = TransactionBuilder(notary).withItems()
                .addOutputState(membershipState, MembershipContract.CONTRACT_NAME)
                .addCommand(txCommand)

        txBuilder.verify(serviceHub)
        val tx = serviceHub.signInitialTransaction(txBuilder)

        return subFlow(FinalityFlow(tx, emptyList()))
    }
}

