Business Networks Toolkit
==================================

This repository contains software to enable Business Networks on Corda. 

Contents:
* [Business Networks Membership Service](./memberships-management) allows to 
    * On-board new members to a business network. 
    * Suspend members from a business network.
    * Distribute membership list to the business network members.
    * Association a custom metadata (such as role, email address, phone number etc.) with membership states.
    * Participate in multiple business networks for a single node.
* [Ledger Sync Service](./ledger-sync) is a collection of flows, designed to check ledger integrity and to recover lost data from counterparts.
* [CorDapp Distribution Service](./cordapp-updates-distribution) allows Business Network Operators to distribute CorDapp updates to their network participants. 

