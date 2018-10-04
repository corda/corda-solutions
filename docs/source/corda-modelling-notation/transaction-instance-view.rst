=========================================
Orchestration – Transaction Instance View
=========================================

To specify the require transaction corresponding to a business event, we use the Transaction Instance view. This consists of writing down the specific transaction that needs to occur for the particular business event.

.. image:: resources/CMN_Transaction_instance_with_arrows.png
  :width: 80%
  :align: center


1. Inputs:

  Show all the input states including the associated contracts, relevant properties and participants. Note the view of the state is modified from the view in the Ledger layer’s State Machine View as we don’t show the constraints on these representations of the State and we explicitly show the actual participants.

2. Transaction Parameters

  Shows the Commands, Actual Signatures, any Attachments, the valid Time-window for notarising the transaction and the participants which consist of the union of the participants in the states

  Note, any extra participants which the transaction will be distributed to via the flows are not shown here as they don’t form part of the transaction.

3. Outputs:

  Show all the output states including the associated contracts, relevant properties and participants. Again, the view of the state is modified from the view in the Ledger layer’s State Machine View as we don’t show the constraints on these representations of the State and we explicitly show the actual participants.
