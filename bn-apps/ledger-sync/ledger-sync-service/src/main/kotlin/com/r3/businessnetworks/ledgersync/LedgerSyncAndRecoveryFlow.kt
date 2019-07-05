package com.r3.businessnetworks.ledgersync

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.utilities.ProgressTracker

@InitiatingFlow
@StartableByRPC
class LedgerSyncAndRecoveryFlow( private var members: List<Party>? ) : FlowLogic<Unit>() {


    constructor() : this(members = null)

    companion object {
        object FIND_MEMBERS : ProgressTracker.Step("Retrieving list of members if not provided")
        object EVALUATE_CONSISTENCY : ProgressTracker.Step("Evaluating ledger inconsistencies for every member of the Network" )
        object RECOVER_FINDINGS : ProgressTracker.Step("Requesting ledger sync updates to parties who we are inconsistent with")
        object RECOVER_TRANSACTIONS : ProgressTracker.Step("Recovering transaction based on received ledger sync updates")
        fun tracker() = ProgressTracker(FIND_MEMBERS, EVALUATE_CONSISTENCY, RECOVER_FINDINGS, RECOVER_TRANSACTIONS)
    }

    override val progressTracker = tracker()
    @Suspendable
    override fun call() {
        progressTracker.currentStep = FIND_MEMBERS
        if (members == null)
        // We recover the members list from the NetworkMap if not provided
            members = this.serviceHub.networkMapCache.allNodes.map { it.legalIdentities.first() }
        logger.info("Members list: $members")
        progressTracker.currentStep = EVALUATE_CONSISTENCY
        val hashFindings = subFlow(EvaluateLedgerConsistencyFlow(members!! - ourIdentity - this.serviceHub.networkMapCache.notaryIdentities))
        members =  hashFindings.filter { !it.value }.map { it.key }
        logger.info("Inconsistent members list: $members")
        progressTracker.currentStep = RECOVER_FINDINGS
        val partiesAndFindings = subFlow(RequestLedgersSyncFlow(members!!))
        progressTracker.currentStep = RECOVER_TRANSACTIONS
        return subFlow(TransactionRecoveryFlow(partiesAndFindings))
    }
}