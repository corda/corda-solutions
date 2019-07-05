package com.r3.businessnetworks.membership.flows.bno

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.flows.bno.service.BNOConfigurationService
import com.r3.businessnetworks.membership.flows.bno.service.DatabaseService
import com.r3.businessnetworks.membership.flows.getAttachmentIdForGenericParam
import com.r3.businessnetworks.membership.flows.isAttachmentRequired
import com.r3.businessnetworks.membership.flows.member.RequestMembershipFlow
import com.r3.businessnetworks.membership.flows.member.Utils.throwExceptionIfNotBNO
import com.r3.businessnetworks.membership.states.MembershipContract
import com.r3.businessnetworks.membership.states.MembershipState
import com.r3.businessnetworks.membership.states.MembershipStatus
import net.corda.core.contracts.Command
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.time.Instant

/**
 * Self Issue a Membership.  This can only be done by a BNO
 */
@StartableByRPC
@InitiatingFlow(version = 2)
open class SelfIssueMembershipFlow(val metaData : Any) : FlowLogic<SignedTransaction>() {
    companion object {
        object RequestingMembership : ProgressTracker.Step("Requesting Membership")
        object ActivatingMembership : ProgressTracker.Step("Activating Membership")

        fun tracker() = ProgressTracker(
                RequestingMembership,
                ActivatingMembership
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call() : SignedTransaction {
        throwExceptionIfNotBNO(ourIdentity, serviceHub)

        progressTracker.currentStep = RequestingMembership
        val stx = subFlow(RequestMembershipFlow(ourIdentity, metaData))

        val outState = stx.tx.outputs.single()
        val membershipState = outState.data as MembershipState<*>

        // If auto activate is set then don't need to call ActivateMembership
        return if (!membershipState.isActive()) {
            logger.info("Membership is not yet active")
            progressTracker.currentStep = ActivatingMembership
            val output = stx.tx.outRefsOfType(MembershipState::class.java).single()

            subFlow(ActivateMembershipFlow(output))
        } else {
            logger.info("Membership was automatically activated")
            stx
        }
    }
}

