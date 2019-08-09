package com.r3.businessnetworks.membership.flows.bno

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.flows.bno.service.BNOConfigurationService
import com.r3.businessnetworks.membership.flows.bno.service.DatabaseService
import com.r3.businessnetworks.membership.flows.member.Utils.throwExceptionIfNotBNO
import com.r3.businessnetworks.membership.states.MembershipContract
import com.r3.businessnetworks.membership.states.MembershipState
import com.r3.businessnetworks.membership.states.MembershipStatus
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

/**
 * Self Issue a Membership.  This can only be done by a BNO,
 * @param metaData BNO data needs a membership and then be activated
 */
@Suppress("UNUSED_VARIABLE", "MemberVisibilityCanBePrivate")

@StartableByRPC
@InitiatingFlow(version = 2)
open class SelfIssueMembershipFlow(val metaData : Any, val networkID: String?) : FlowLogic<SignedTransaction>() {

    companion object {
        object ACTIVATED_MEMBERSHIP : ProgressTracker.Step("Membership Activated")
        object ACTIVATING_MEMBERSHIP : ProgressTracker.Step("Activating Membership")

        fun tracker() = ProgressTracker(
                ACTIVATED_MEMBERSHIP,
                ACTIVATING_MEMBERSHIP
        )
    }

    override val progressTracker = tracker()


     fun createMembership(): SignedTransaction{
        throwExceptionIfNotBNO(ourIdentity, serviceHub)
        val configuration = serviceHub.cordaService(BNOConfigurationService::class.java)
        val notary = configuration.notaryParty()
        //Start of first transaction to request a membership state
        val membership: MembershipState<Any> = MembershipState(ourIdentity, ourIdentity, metaData,networkID)
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        val counterpartMembership = databaseService.getMembership(ourIdentity, ourIdentity)
        if (counterpartMembership != null) {
            throw FlowException("Membership already exists")
        }
        val builder = TransactionBuilder(notary)
                .addOutputState(membership, MembershipContract.CONTRACT_NAME)
                .addCommand(MembershipContract.Commands.Request(), ourIdentity.owningKey)
        builder.verify(serviceHub)
        val grantMembershipStx = serviceHub.signInitialTransaction(builder)
        subFlow(FinalityFlow(grantMembershipStx, emptyList()))
        //end of first transaction to request a membership state
        return grantMembershipStx
    }

    @Suspendable
    override fun call(): SignedTransaction{
        //start of second transaction to set membership status to ACTIVE
        val bnoMembership = createMembership().tx.outRefsOfType(MembershipState::class.java).single()
        val configuration = serviceHub.cordaService(BNOConfigurationService::class.java)
        val notary = configuration.notaryParty()
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
            val activateMembershipStx = serviceHub.signInitialTransaction(txBuilder)
            // sign the transaction so it can be written to the ledger
            subFlow(FinalityFlow(activateMembershipStx, listOf())) //listOf remains empty since only BNO needed to sign the transaction
            logger.info("Membership has been activated")
            progressTracker.currentStep = ACTIVATED_MEMBERSHIP
            return subFlow(FinalityFlow(activateMembershipStx,  emptyList()))
            //end of second transaction to set membership status to ACTIVE
    }
}
