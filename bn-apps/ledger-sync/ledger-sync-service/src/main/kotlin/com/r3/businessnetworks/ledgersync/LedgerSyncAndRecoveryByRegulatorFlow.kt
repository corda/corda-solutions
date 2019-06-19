package com.r3.businessnetworks.ledgersync

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.contextLogger

/**
 * This flow is used to recovery a Party node transactions records from the ledger data of Regulator node. 
 */
@InitiatingFlow
@StartableByRPC
class LedgerSyncAndRecoveryByRegulatorFlow( private var regulators: List<Party>? ) : FlowLogic<Unit>() {

    constructor() : this(regulators = null)

    companion object {
        object FIND_REGULATORS : ProgressTracker.Step("Retrieving list of regulators if not provided")
        object RECOVER_FINDINGS : ProgressTracker.Step("Requesting ledger sync updates between party and regulator nodes who we are inconsistent with")
        object RECOVER_TRANSACTIONS : ProgressTracker.Step("Recovering transactions for the party from the regulator based on received ledger sync updates")
        fun tracker() = ProgressTracker(FIND_REGULATORS, RECOVER_FINDINGS, RECOVER_TRANSACTIONS)
    }

    override val progressTracker = tracker()
    @Suspendable
    override fun call() {
        progressTracker.currentStep = FIND_REGULATORS
        if (regulators == null)
         //We recover the regulators list from the NetworkMap if not provided
            regulators = this.serviceHub.networkMapCache.regulatorNodes.map { it.legalIdentities.first() }
        logger.info("Regulators list: $regulators")

        progressTracker.currentStep = RECOVER_FINDINGS
        val partiesAndFindings = subFlow(RequestRegulatorLedgerSyncFlow(regulators!!))

        progressTracker.currentStep = RECOVER_TRANSACTIONS
        return subFlow(TransactionRecoveryFlow(partiesAndFindings))
    }
}
