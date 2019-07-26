package com.r3.businessnetworks.membership.flows.bno

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.flows.bno.service.BNOConfigurationService
import com.r3.businessnetworks.membership.flows.bno.support.BusinessNetworkOperatorFlowLogic
import com.r3.businessnetworks.membership.flows.member.Utils.throwExceptionIfNotBNO
import com.r3.businessnetworks.membership.states.MembershipContract
import com.r3.businessnetworks.membership.states.MembershipState
import com.r3.businessnetworks.membership.states.MembershipStatus
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

/**
 * Self Issue a Membership.  This can only be done by a BNO,
 * @param bnoMembership bno membership state to be activated
 */
@Suppress("UNUSED_VARIABLE")
@StartableByRPC
@InitiatingFlow(version = 2)
open class SelfIssueMembershipFlow(val bnoMembership: StateAndRef<MembershipState<Any>>) : FlowLogic<SignedTransaction>() {
    companion object {
        object ACTIVAED_MEMBERSHIP: ProgressTracker.Step("Membership Activated")
        object ACTIVATING_MEMBERSHIP : ProgressTracker.Step("Activating Membership")
        
        fun tracker() = ProgressTracker(
                ACTIVATED_MEMEBERSHIP,
                ACTIVATING_MEMBERSHIP
        )
    }

    override val progressTracker = tracker()

    @Suspendable

    //The call function will build a transaction which will only
    // modify the BNO status and then self sign the transaction
    override fun call(): SignedTransaction {
        // stop if Node is not a BNO
        throwExceptionIfNotBNO(ourIdentity, serviceHub)
        val configuration = serviceHub.cordaService(BNOConfigurationService::class.java)
        val notary = configuration.notaryParty()
        logger.info("Membership is being activated")
        progressTracker.currentStep = ACTIVATING_MEMBERSHIP


            val txBuilder = TransactionBuilder(notary)
                .addInputState(bnoMembership)
                //transaction will only modify the status of the BNO node and set it to ACTIVE
                .addOutputState(bnoMembership.state.data.copy(
                                status = MembershipStatus.ACTIVE,
                                modified = serviceHub.clock.instant()),
                                MembershipContract.CONTRACT_NAME)
                .addCommand(MembershipContract.Commands.Activate(), ourIdentity.owningKey)

            txBuilder.verify(serviceHub)
            val stx = serviceHub.signInitialTransaction(txBuilder)
            // sign the transaction so it can be written to the ledger
            subFlow(FinalityFlow(stx, listOf())) //listOf remains empty since only BNO needed to sign the transaction
            logger.info("Membership has been activated")
            progressTracker.currentStep = ACTIVATED_MEMBERSHIP
            return subFlow(FinalityFlow(stx, listOf()))

    }
}

/**
 * convenience flow that can be used from the command line
 *
 * @param party whose membership state to be activated
 */
@InitiatingFlow
@StartableByRPC
open class ActivateBnoMembershipFlow(val party: Party) : BusinessNetworkOperatorFlowLogic<SignedTransaction>() {

    companion object {
        object LOOKING_FOR_MEMBERSHIP_STATE : ProgressTracker.Step("Looking for party's membership state")
        object ACTIVATING_THE_MEMBERSHIP_STATE : ProgressTracker.Step("Activating the membership state")

        fun tracker() = ProgressTracker(
                LOOKING_FOR_MEMBERSHIP_STATE,
                ACTIVATING_THE_MEMBERSHIP_STATE
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = LOOKING_FOR_MEMBERSHIP_STATE
        val stateToActivate = findMembershipStateForParty(party)

        progressTracker.currentStep = ACTIVATING_THE_MEMBERSHIP_STATE
        return subFlow(SelfIssueMembershipFlow(stateToActivate))
    }

}


