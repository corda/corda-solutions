package com.r3.businessnetworks.membership.bno

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.member.OnBoardingRequest
import com.r3.businessnetworks.membership.member.RequestMembershipFlow
import com.r3.businessnetworks.membership.bno.service.BNOConfigurationService
import com.r3.businessnetworks.membership.bno.service.DatabaseService
import com.r3.businessnetworks.membership.bno.support.BusinessNetworkOperatorFlowLogic
import com.r3.businessnetworks.membership.states.MembershipContract
import com.r3.businessnetworks.membership.states.MembershipState
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import java.sql.SQLException

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
class RequestMembershipFlowResponder(val session : FlowSession) : BusinessNetworkOperatorFlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        // checking that there is no existing membership state
        val counterparty = session.counterparty
        val configuration = serviceHub.cordaService(BNOConfigurationService::class.java)
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        val counterpartMembership = databaseService.getMembership(counterparty, ourIdentity, configuration.membershipContractName())
        if (counterpartMembership != null) {
            throw FlowException("Membership already exists")
        }

        // creating a pending request to make sure that no multiple on-boarding request can be in-flight in the same time
        try {
            databaseService.createPendingMembershipRequest(session.counterparty)
        } catch (e : SQLException) {
            logger.warn("Error when trying to create a pending membership request", e)
            throw FlowException("Membership request already exists")
        }

        val membership : MembershipState<Any>
        // Issuing PENDING membership state onto the ledger
        try {
            // receive an on-boarding request
            val request = session.receive<OnBoardingRequest>().unwrap { it }

            val notary = configuration.notaryParty()

            // issue pending membership state on the ledger
            membership = MembershipState(counterparty, ourIdentity, request.metadata)
            val builder = TransactionBuilder(notary)
                    .addOutputState(membership, configuration.membershipContractName())
                    .addCommand(MembershipContract.Commands.Request(), counterparty.owningKey, ourIdentity.owningKey)
            builder.verify(serviceHub)
            val selfSignedTx = serviceHub.signInitialTransaction(builder)
            val allSignedTx = subFlow(CollectSignaturesFlow(selfSignedTx, listOf(session)))
            subFlow(FinalityFlow(allSignedTx))
        } finally {
            try {
                logger.info("Removing the pending request from the database")
                databaseService.deletePendingMembershipRequest(session.counterparty)
            } catch (e : SQLException) {
                logger.warn("Error when trying to delete pending membership request", e)
            }
        }

        if (activateRightAway(membership, configuration)) {
            logger.info("Auto-activating membership for party ${membership.member}")
            val stateToActivate = findMembershipStateForParty(membership.member)
            subFlow(ActivateMembershipFlow(stateToActivate))
        }
    }

    private fun activateRightAway(membershipState : MembershipState<Any>, configuration : BNOConfigurationService) : Boolean {
        return configuration.getMembershipAutoAcceptor()?.autoActivateThisMembership(membershipState) ?: false
    }
}