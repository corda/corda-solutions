package net.corda.businessnetworks.ledgersync

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.ContractState
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.MAX_PAGE_SIZE
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.unwrap

@InitiatingFlow
class RequestLedgersSyncFlow(
        private val members: List<Party>
) : FlowLogic<Map<Party, LedgerSyncFindings>>() {

    @Suspendable
    override fun call(): Map<Party, LedgerSyncFindings> = (members - ourIdentity)
            .map { they ->
                val knownTransactionsIds = serviceHub.vaultService.withParticipants(ourIdentity, they)
                val findings = initiateFlow(they).sendAndReceive<LedgerSyncFindings>(knownTransactionsIds).unwrap { it }
                they to findings
            }.toMap()
}

@Suppress("unused")
@InitiatedBy(RequestLedgersSyncFlow::class)
class RespondLedgerSyncFlow(
        private val otherSideSession: FlowSession
) : FlowLogic<Unit>() {

    /**
     * Depending on the use case, users of this flow might introduce additional validation logic. This could mean
     * ensuring the requester is still a valid member of a network at the time of request or similar checks.
     */
    @Suspendable
    override fun call() {
        val theirs = otherSideSession.receive<List<SecureHash>>().unwrap { it }
        val ours = serviceHub.vaultService.withParticipants(ourIdentity, otherSideSession.counterparty)
        otherSideSession.send(LedgerSyncFindings(
                ours - theirs,
                theirs - ours
        ))
    }
}

/**
 * Provides a list of transaction hashes referring to transactions in which all of the given parties are participating.
 * Note that this can be a lengthy list and no precautions are taken to ensure the output does not exceed the maximum
 * message size.
 */
private fun VaultService.withParticipants(vararg parties: Party): List<SecureHash> =
        queryBy<ContractState>(
                QueryCriteria.VaultQueryCriteria(),
                PageSpecification(1, MAX_PAGE_SIZE)
        ).states.filter {
            it.state.data.participants.containsAll(parties.toList())
        }.map {
            it.ref.txhash
        }

/**
 * A class that encapsulates the findings of a [RequestLedgersSyncFlow], indicating which transactions the original
 * party and the counter party are respectively missing.
 *
 * Here, the "requester" is the party making the original sync flow request and the "requestee" is the counter party.
 */
@CordaSerializable
data class LedgerSyncFindings(
        val missingAtRequester: List<SecureHash>,
        val missingAtRequestee: List<SecureHash>
)
