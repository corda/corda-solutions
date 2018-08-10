===================
Ledger Sync Service
===================

The ledger sync service is a collection of three flows, designed to recover contract transactions shared with nodes in a `business network <../memberships-management>`_.

Flows can be composed to a four-step process from the perspective of a node that suspects transactions have been lost in the following way:

 1. Determine potential counter parties by `querying the business network operator <../membership-service/src/main/kotlin/net/corda/businessnetworks/membership/member/GetMembershipsFlow.kt>`_.
 2. `Evaluate pairwise if the ledger is consistent <src/main/kotlin/net/corda/businessnetworks/ledgersync/EvaluateLedgerConsistencyFlow.kt>`_ with regards to the transactions the counter party holds.
 3. If inconsistencies are flagged, the counter party can be queried for a more detailed report based on which both the parties can take further action (such as notifying the BNO) or recover the transactions found.
 4. Transaction IDs can then be used in ??? to recover individual transactions from counter parties.
