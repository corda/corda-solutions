
================
Receipts Pattern
================

:Status: Ready for review
:Implemented: No (abstract/ foundational Pattern)

-------
Summary
-------

Receipts pattern can be used when the transitions of a state chain (EnablerState) must be evidenced within other transactions (EnabledTransaction) to allow  the transitions in those transactions to take palace, specifically where:

 - Each EnablerState transition should enable only one EnabledTransaction, no double spends.
 - Being a participant on the EnablerState chain, does not give the ability to resolve the EnabledTransactions. (Although some participants will also be participants on the EnabledTransactions)
 - Being a participant on an EnabledTransaction does not giver the ability to resolve details on other EnabledTransactions. (Although some participants will also be participants on other EnabledTransactions)
 - The EnablerState Chain can store cumulative information about the Receipts it has created.



------------------------
Explanation/ Walkthrough
------------------------

As a foundational pattern, the explanation may be a little abstract, for a more concrete pattern which uses the Receipts pattern, see Billing with Receipts. Alternatively, it maybe helpful to imagine the EnablingState to be some form of payment token and the EnabledTransaction to be a business event that can only happen once payment has been proved to have occurred, in which case it extends to the Token Receipts pattern.

The pattern can be illustrated as follows, using a State Evolution diagram with Privacy overlay (the Corda Modelling Notation section for details on how to read the diagram):

.. image:: resources/P_Receipts_state_evolution.png
  :width: 100%
  :align: center

1. The Contract for the EnabledState specifies that the transition 'EnabledCommand' is not permitted to occur unless the EnabledTransaction contains a ReceiptState showing that CommandWithRecipt transition has occurred on the EnablerState Chain.

2. Prior to EnabledTransaction 1 taking place, whoever has permission to execute the CommandWithRecipt transition must execute the CommandWithReceipt transition thus generating a ReceiptState. The Contract for the EnablerState must ensure that the Receipt is provably related to the specific transition which created it, not any other in the Enabler State chain.

3. The ReceiptState must contain whatever data is required in the Contract governing the EnabledCommand.

4. Once the ReceiptState has been created, it can be used in EnabledTransaction to enable the EnabledCommand Transition on EnabledState.

5. Prior to EnabledTransaction 2 taking place, whoever has permission to execute the CommandWithReceipt Transition must execute another CommandWithReceipt transition thus generating the second (distinct) ReceiptState.

6. The Second ReceiptState can now be used to enable EnabledTransaction 2.


----------------
Privacy Analysis
----------------

The crux of the pattern is its privacy characteristics. There are two important characteristics:

1) Privacy between EnabledTransactions:

 Let's assume that a subset of the participants in EnabledTransaction 2 are not allowed to know about EnabledTransaction 1, for example if they are competitors. When the participants of EnabledTransaction 2 resolves the transaction they will resolve back to the EnablerState chain, they will at no point resolve EnabledTransaction 1. This is shown by the red Privacy trace, which considers what the 'CompetitorParty' participant must resolve.

 This assumes that 'CompetitorParty' is not a participant on the EnabledTransaction 1, because then they'd get to see EnabledTransaction 1 anyway.

2) Privacy from EnablerState Participants

When the participants on the EnablerState chain resolve their transactions they will only resolve down the EnablerState chain, they will never resolve any of the EnabledTransactions which the ReceiptStates were used to enable. This is shown by the blue Privacy trace.

This assumes that the participants on the EnablerState chain are not participants on the EnabledTransactions, because then they'd get to see the EnabledTransaction anyway.


----------
Extensions
----------

The receipts pattern can be extended to:

 - Billing with tokens
 - Token Receipts
 - Regulators: generically, this pattern can be used where some subset of the EnablingTransaction needs to be proved to the EnabledTransaction. This would be very powerful for regulators who need to collect some sub set of transaction data from every transaction in a market without being party to all the transaction information.
 - ** need to brainstorm out more **

--------------------------------------------------
Things to Consider when using the Receipts pattern
--------------------------------------------------

  - Who has the right to issue the EnablerState?
  - Who has the right to create the ReceiptStates?
  - Who is in control of the Contract code which dictates the conditions for a valid transition of the states in the EnabledTransaction?
  - Ensure the privacy requirements of each participant are well understood.
  - Is the EnablerState Contract Code robust enough to stop ReceiptStates being created without a matching/ appropriate Transition in the EnablerState? Eg if the Enabler State tracks a cumulative value, does the cumulative amount increment by the amount evidenced in the ReceiptState?
  - Is the Contract Code in the EnabledState robust enough to ensure that the EnabledCommand Transition cannot take place with out an appropriate ReceiptState?

----------------------
Related Anti-patterns
----------------------

A related pattern could use reference states to evidence the EnablerState transition. This could be achieved either from including the ReceiptState as a reference state in the EnabledTransaction, or doing away with the ReceiptState entirely and directly including the EnablerState as a reference state in the EnabledTransaction.

However, if a reference state is used, then there is a potential double spend problem. In some cases this is fine, for instance if the ReceiptState/EnablerState contains current reference data, however, if it contains evidence of a transfer of value, then there is the potential for a double spend. ie one payment being used to pay for two or more business actions.
