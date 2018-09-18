package net.corda.businessnetworks.membership.bno

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.member.OnBoardingRequest
import net.corda.businessnetworks.membership.member.RequestMembershipFlow
import net.corda.businessnetworks.membership.bno.service.BNOConfigurationService
import net.corda.businessnetworks.membership.bno.service.DatabaseService
import net.corda.businessnetworks.membership.bno.support.BusinessNetworkOperatorFlowLogic
import net.corda.businessnetworks.membership.states.Membership
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
 */
@InitiatedBy(RequestMembershipFlow::class)
class RequestMembershipFlowResponder(val session : FlowSession) : BusinessNetworkOperatorFlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        // checking that there is no existing membership state
        val counterparty = session.counterparty
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        val counterpartMembership = databaseService.getMembership(counterparty)
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

        // Issuing PENDING membership state onto the ledger
        try {
            // receive an on-boarding request
            val request = session.receive<OnBoardingRequest>().unwrap { it }

            val configuration = serviceHub.cordaService(BNOConfigurationService::class.java)
            val notary = configuration.notaryParty()

            // issue pending membership state on the ledger
            val membership = Membership.State(counterparty, ourIdentity, request.metadata)
            val builder = TransactionBuilder(notary)
                    .addOutputState(membership, Membership.CONTRACT_NAME)
                    .addCommand(Membership.Commands.Request(), counterparty.owningKey, ourIdentity.owningKey)
            builder.verify(serviceHub)
            val selfSignedTx = serviceHub.signInitialTransaction(builder)
            val allSignedTx = subFlow(CollectSignaturesFlow(selfSignedTx, listOf(session)))
            subFlow(FinalityFlow(allSignedTx))

            if(activateRightAway(membership, configuration)) {
                logger.info("Auto-activating membership for party ${membership.member}")
                val stateToActivate = findMembershipStateForParty(membership.member)
                subFlow(ActivateMembershipFlow(stateToActivate))
            }
        } finally {
            try {
                logger.info("Removing the pending request from the database")
                databaseService.deletePendingMembershipRequest(session.counterparty)
            } catch (e : SQLException) {
                logger.warn("Error when trying to delete pending membership request", e)
            }
        }
    }

    private fun activateRightAway(membershipState : Membership.State, configuration : BNOConfigurationService) : Boolean {
        return configuration.getMembershipAutoAcceptor()?.autoActivateThisMembership(membershipState) ?: false
    }
}