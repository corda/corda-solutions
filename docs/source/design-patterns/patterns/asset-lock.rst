======================
Asset Lock Pattern
======================

:Status: Peer reviewed
:Implemented: No

-------
Summary
-------

    - An Asset lock is a mechanism, used during a two-party agreement, that prevents one party spending or distributing the asset involved in the agreement, elsewhere.

    - The lock enables a named asset to be locked out of use until another event has happened which, only then allows the asset to be unlocked.

    - This pattern can allow two mutually distrusting partys to perform an atomic swap of two assets without the need our a third party to validate the transaction.

-------
Context
-------

When we want to transfer an asset state from one party to another, we often need to ‘freeze’ the asset while the transaction occurs, as if to put it in a kind of escrow until another event or transaction has occurred.
One of the most common examples of this kind of event would be a payment for the asset such that the asset is locked until the new owner has made payment. Once payment or proof-of-payment has occured the lock can be lifted, allowing the buyer to accept ownership of the asset. This pattern is not restricted to payments and could be used for essentially any state change at all.

-------
Problem
-------

The need for such a lock stems from the potential for nefarious actors attempting to:

    - Transact the same asset with a different party while a deal is underway.
    - Spend states away mid-pattern to benefit one party.
    - Offer a state but substitute a fake state (i.e. one without the assumed provenance)
    - Pledge a state from an invalid transaction.
    - Use a state to power two transitions of the same type
    - Use a state to power more than one transaction using reference states

---------------
Salient Factors
---------------

The challenge is to set up the lock so that the lock can be released by somebody other than the owner once they have provided some consideration.
This is difficult as the asset's own contract rules are likely to state that the current owner needs to sign to transfer ownership.

------------------------
Solution Walkthrough
------------------------

State Evolution diagram with Privacy overlay (see the CorDapp Design Language section for details on how to read the diagram):

a) *Description & Walkthrough*

.. image:: resources/Asset-Lock-option-3.png
  :width: 80%
  :align: center

This asset transfer also occurs over two transactions.

**Tx 1**: involves the preparation of the asset state for transfer via the addition of fields and conditions. This happens by consuming the input AssetState to a new ‘twin’ output AssetState. This output AssetState contains the following properties:


    - `currentOwner`: the current owner of the asset
    - `newOwner`: the new owner of the asset, the buyer.
    - `precondition`: this is some event that must occur before the state can be consumed. The condition could be the presence of the correct ConsiderationState in a transaction with it.
    - `considerationState`: this a state that acts as either payment or proof-of-payment between A and B.
    - A reference ID to be used in the consideration later on.

**Tx 2**: In order for the transfer of the AssetState to occur, the contract rules of the twin AssetState must be met. These include that the precondition is satisfied and that there is a ConsiderationState with the correct reference ID also present in the transaction.


b. *Analysis & Considerations*

- Since the original asset is consumed as part of Tx 1, Party A cannot sell the asset to a different party while a deal is underway or anywhere mid-pattern.
- Since the AssetState new owner (PartyB) is referenced in the twin AssetStatein Tx 1 it is not possible to sneakily change the new owner to some other Party in Tx 2.
- Party B cannot consume a state in an invalid Tx 2 because if Tx 1 fails then the twin AssetState will not be created as an output state, thus Tx 2 cannot happen.
- Since the twin AssetState is consumed it cannot be deviously be reused in multiple transactions similar to Tx 2. The same applies to ConsiderationState.
- Reference states are not used as part of this transaction so there is no risk of one state being used in multiple transactions.
- It is mandatory that the owners signatue is not required to transfer ownership of the asset in Tx 2, other the payment could be made and the Asset never transferred.
- Since it is a bilateral agreement, privacy is shared between the two participants. Privacy of the consideration is a fundamental part of the Receipts pattern design.


- There is a concern that the receiving party could alter the FinalityFlow such that the Consideration/Payment would be notarised but then the actual state not passed over to the selling party. This might not even involve altering the FinalityFlow and could just be blocking the packets from reaching the counterparty.
- This means the buyer could end up with both the consideration **and** the asset.
- This concern may be counteracted by allowing the selling party to initiate the transaction such that they are responsible for sending the states across. This may however bring about the same trust issue, but in reverse.
- There might be potential for a custom *ReverseFinalityFlow* that is called on the responder side such that they can notarise the signed transaction and broadcast the states.


This pattern doesn’t actually lock the asset, so not an asset lock, it only gives the ability for someone other than the owner to move the asset.


----------------
Privacy Analysis
----------------

There are two important privacy characteristics to consider:

1) Confidential identities.

  In order to avoid Participants on the AssetState chain seeing who owned the AssetState before them, which may leak confidential information, Confidential identities should be used in the AssetStates.


---------------------------
Extensions - to investigate
---------------------------
 - Timewindows to allow the asset to be unlocked.
 - Investigate solutions to the broadcast problem - notary broadcasting, nodes querying notary to confirm state spent etc.
 - Combining with Token Receipt pattern to prevent the spending away of the EnabledState once Payment has been occured
