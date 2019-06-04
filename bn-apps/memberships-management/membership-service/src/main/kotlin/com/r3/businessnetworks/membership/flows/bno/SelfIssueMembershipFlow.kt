package com.r3.businessnetworks.membership.flows.bno

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.flows.bno.service.BNOConfigurationService
import com.r3.businessnetworks.membership.flows.bno.service.DatabaseService
import com.r3.businessnetworks.membership.flows.getAttachmentIdForGenericParam
import com.r3.businessnetworks.membership.flows.isAttachmentRequired
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
    override fun call(): SignedTransaction {
        throwExceptionIfNotBNO(ourIdentity, serviceHub)

        val config = serviceHub.cordaService(BNOConfigurationService::class.java)
        val notary = config.notaryParty()
        val pendingState = MembershipState(this.ourIdentity, this.ourIdentity, metaData, status = MembershipStatus.PENDING)
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)

        val bnoMember = databaseService.getMembership(ourIdentity, ourIdentity)
        if (bnoMember != null) {
            throw FlowException("Membership already exists")
        }

        // Request Membership

        progressTracker.currentStep = RequestingMembership

        val requestTxBuilder = TransactionBuilder(notary).withItems()
                .addOutputState(pendingState, MembershipContract.CONTRACT_NAME)
                .addCommand(Command(MembershipContract.Commands.Request(), ourIdentity.owningKey))

        requestTxBuilder.verify(serviceHub)

        if (pendingState.isAttachmentRequired())
            requestTxBuilder.addAttachment(pendingState.getAttachmentIdForGenericParam())

        val requestTx = serviceHub.signInitialTransaction(requestTxBuilder)
        val f1 = subFlow(FinalityFlow(requestTx, emptyList()))
        val requestState = f1.tx.outRef<MembershipState<Any>>(0)

        // Activate Membership

        progressTracker.currentStep = ActivatingMembership

        val activateState = requestState.state.data.copy(status = MembershipStatus.ACTIVE, modified = Instant.now())

        val activateTxBuilder = TransactionBuilder(notary).withItems()
                .addInputState(requestState)
                .addOutputState(activateState,
                        MembershipContract.CONTRACT_NAME)
                .addCommand(Command(MembershipContract.Commands.Activate(), ourIdentity.owningKey))

        if (activateState.isAttachmentRequired())
            activateTxBuilder.addAttachment(pendingState.getAttachmentIdForGenericParam())

        activateTxBuilder.verify(serviceHub)

        val activateTx = serviceHub.signInitialTransaction(activateTxBuilder)

        return subFlow(FinalityFlow(activateTx, emptyList()))
    }
}

