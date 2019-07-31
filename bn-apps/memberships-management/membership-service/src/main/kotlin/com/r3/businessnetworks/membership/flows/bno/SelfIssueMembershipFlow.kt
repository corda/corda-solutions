package com.r3.businessnetworks.membership.flows.bno

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.flows.bno.service.BNOConfigurationService
import com.r3.businessnetworks.membership.flows.bno.service.DatabaseService
import com.r3.businessnetworks.membership.flows.bno.support.BusinessNetworkOperatorFlowLogic
import com.r3.businessnetworks.membership.flows.member.Utils.throwExceptionIfNotBNO
import com.r3.businessnetworks.membership.states.MembershipContract
import com.r3.businessnetworks.membership.states.MembershipState
import com.r3.businessnetworks.membership.states.MembershipStatus
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

/**
 * Self Issue a Membership.  This can only be done by a BNO,
 * @param metaData BNO data needs a membership and then be activated
 */
@Suppress("UNUSED_VARIABLE")
@StartableByRPC
@InitiatingFlow(version = 2)
open class SelfIssueMembershipFlow(val metaData : Any) : FlowLogic<SignedTransaction>() {
    companion object {
        object ACTIVATED_MEMBERSHIP : ProgressTracker.Step("Membership Activated")
        object ACTIVATING_MEMBERSHIP : ProgressTracker.Step("Activating Membership")

        fun tracker() = ProgressTracker(
                ACTIVATED_MEMBERSHIP,
                ACTIVATING_MEMBERSHIP
        )
    }

    override val progressTracker = tracker()

    @Suspendable

    override fun call(): SignedTransaction {
        throwExceptionIfNotBNO(ourIdentity, serviceHub)
        val configuration = serviceHub.cordaService(BNOConfigurationService::class.java)
        val notary = configuration.notaryParty()

        //Start of first transaction to request a membership state
        val membership: MembershipState<Any>
        membership = MembershipState(ourIdentity, ourIdentity, metaData)
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        val counterpartMembership = databaseService.getMembership(ourIdentity, ourIdentity)
        if (counterpartMembership != null) {
            throw FlowException("Membership already exists")
        }
        val builder = TransactionBuilder(notary)
                    .addOutputState(membership, MembershipContract.CONTRACT_NAME)
                    .addCommand(MembershipContract.Commands.Request(),ourIdentity.owningKey)
        builder.verify(serviceHub)
        val selfSignedTx = serviceHub.signInitialTransaction(builder)
        subFlow(FinalityFlow(selfSignedTx, listOf()))
        //end of first transaction to request a membership state

        //start of second transaction to set membership status to ACTIVE
        val bnoMembership = selfSignedTx.tx.outRefsOfType(MembershipState::class.java).single()
        logger.info("Membership is being activated")
        progressTracker.currentStep = ACTIVATING_MEMBERSHIP
            val txBuilder = TransactionBuilder(notary)
                .addInputState(bnoMembership)
                //Second transaction will only modify the status of the BNO node and set it to ACTIVE
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
            //end of second transaction to set membership status to ACTIVE
    }
}
