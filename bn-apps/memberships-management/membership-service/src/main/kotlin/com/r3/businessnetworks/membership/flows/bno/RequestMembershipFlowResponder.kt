package com.r3.businessnetworks.membership.flows.bno

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.flows.bno.service.BNOConfigurationService
import com.r3.businessnetworks.membership.flows.bno.service.DatabaseService
import com.r3.businessnetworks.membership.flows.bno.support.BusinessNetworkOperatorFlowLogic
import com.r3.businessnetworks.membership.flows.getAttachmentIdForGenericParam
import com.r3.businessnetworks.membership.flows.isAttachmentRequired
import com.r3.businessnetworks.membership.flows.member.OnBoardingRequest
import com.r3.businessnetworks.membership.flows.member.RequestMembershipFlow
import com.r3.businessnetworks.membership.states.MembershipContract
import com.r3.businessnetworks.membership.states.MembershipState
import net.corda.core.flows.*
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import javax.persistence.PersistenceException

/**
 * The flow issues a PENDING membership state onto the ledger. After the state is issued, the BNO is supposed to perform
 * required governance / KYC checks / paperwork and etc. After all of the required activities are completed, the BNO can activate membership
 * via [ActivateMembershipFlow].
 *
 * The flow supports automatic membership activation via [MembershipAutoAcceptor].
 *
 * TODO: remove MembershipAutoAcceptor in favour of flow overrides when Corda 4 is released
 */
@InitiatedBy(RequestMembershipFlow::class)
open class RequestMembershipFlowResponder(val session: FlowSession) : BusinessNetworkOperatorFlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        // checking that there is no existing membership state
        val request = session.receive<OnBoardingRequest>().unwrap { it }
        val counterparty = session.counterparty
        val configuration = serviceHub.cordaService(BNOConfigurationService::class.java)
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        val counterpartMembership = databaseService.getMembershipOnNetwork(counterparty, ourIdentity, request.networkID)
        if (counterpartMembership != null) {
            throw FlowException("Membership already exists")
        }

        // creating a pending request to make sure that no multiple on-boarding request can be in-flight in the same time
        try {
            databaseService.createPendingMembershipRequest(session.counterparty)
        } catch (e: PersistenceException) {
            logger.warn("Error when trying to create a pending membership request", e)
            throw FlowException("Membership request already exists")
        }

        val membership: MembershipState<Any>
        // Issuing PENDING membership state onto the ledger
        try {
            // receive an on-boarding request
            //val request = session.receive<OnBoardingRequest>().unwrap { it }

            val notary = configuration.notaryParty()

            // issue pending membership state on the ledger
            membership = MembershipState(counterparty, ourIdentity, request.metadata, request.networkID)
            val builder = TransactionBuilder(notary)
                    .addOutputState(membership, MembershipContract.CONTRACT_NAME)
                    .addCommand(MembershipContract.Commands.Request(), counterparty.owningKey, ourIdentity.owningKey)

            if (membership.isAttachmentRequired())
                builder.addAttachment(membership.getAttachmentIdForGenericParam())

            verifyTransaction(builder)

            val selfSignedTx = serviceHub.signInitialTransaction(builder)
            val allSignedTx = if (membership.bno != membership.member)
                subFlow(CollectSignaturesFlow(selfSignedTx, listOf(session)))
            else
                selfSignedTx

            if (session.getCounterpartyFlowInfo().flowVersion == 1) {
                subFlow(FinalityFlow(allSignedTx))
            } else {
                if (membership.bno == membership.member) {
                    subFlow(FinalityFlow(allSignedTx, listOf()))
                } else {
                    subFlow(FinalityFlow(allSignedTx, listOf(session)))
                }
            }

            if (activateRightAway(membership, configuration)) {
                logger.info("Auto-activating membership for party ${membership.member}")
                val stateToActivate = findMembershipStateForParty(membership.member, request.networkID)
                val tx = subFlow(ActivateMembershipFlow(stateToActivate))
                if (membership.bno == membership.member) session.send(tx)
            } else if (membership.bno == membership.member) {
                session.send(allSignedTx)
            }
        } finally {
            try {
                logger.info("Removing the pending request from the database")
                databaseService.deletePendingMembershipRequest(session.counterparty)
            } catch (e: PersistenceException) {
                logger.warn("Error when trying to delete pending membership request", e)
            }
        }
    }

    /**
     * Override this method to automatically accept memberships
     * See: https://docs.corda.net/head/flow-overriding.html
     */
    @Suspendable
    protected open fun activateRightAway(membershipState: MembershipState<Any>, configuration: BNOConfigurationService): Boolean {
        return false
    }

    /**
     * Override this method to add custom verification membership metadata verifications.
     * See: https://docs.corda.net/head/flow-overriding.html
     */
    @Suspendable
    protected open fun verifyTransaction(builder: TransactionBuilder) {
        builder.verify(serviceHub)
    }
}