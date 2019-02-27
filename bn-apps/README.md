Business Networks Toolkit
==================================

This repository contains software to enable Business Networks on Corda. 

Contents:
* [Business Networks Membership Service](./memberships-management) allows 
    * To on-board new members to a business network. 
    * To suspend members from a business network.
    * To distribute membership list to the business network members.
    * To associate a custom metadata (such as role, email address, phone number etc.) with a node's identity.
    * A single node to participate in multiple business networks.
* [Ledger Sync Service](./ledger-sync) is a collection of flows, designed to check ledger integrity and to recover lost data from counterparts.
* [CorDapp Distribution Service](./cordapp-updates-distribution) allows Business Network Operators to distribute CorDapp updates to their network participants. 
* [Billing Service](./billing) can be used to implement billing and metering on Business Networks.
