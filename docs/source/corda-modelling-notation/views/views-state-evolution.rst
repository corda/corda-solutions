========================
State Evolution (Ledger)
========================

The State Evolution view aims to show how a State may evolve over time. You can think of it as one of many potential paths through the transitions permitted by a State Machine shown in the State Machine View.

-----------------------------
Basic State Evolution Diagram
-----------------------------

Let's start with the State Machine example from the State Machine Section.

.. image:: ../resources/views/CMN2_SM_Full_example.png
  :width: 100%
  :align: center


A State Evolution maps out a potential route through this State Machine, for example:


.. image:: ../resources/views/CMN2_SE_State_evolution.png
  :width: 100%
  :align: center


1. State

  Base representation of the State is consistent with other CDL views, how ever this represents an instance of a state rather than the generic behaviour of the state. the analogy would be an object vs it's class.

2. State Variable

  The generic variable types are replaced by specific values of the variables, for example instead of 'Seller: Party' it is Seller: 'WidgetCo'

3. Constraints

  There are no constraints shown, this is just an example State evolution. If the State Machine constraints are broken then it is not a valid state evolution (which might be the purpose of the diagram when documenting an anti-pattern)

4. Signers

  Signing Constraints have been replaced with actual Signers

5. Multiplicities

  There are no multiplicities as these are implicit in the diagram, eg. if there are 2 output states, then two output states are shown.


---------------
Reference State
---------------

Reference states are supported from Corda v4. A reference input state is a ContractState which can be referred to in a transaction by the contracts of input and output states but whose contract is not executed as part of the transaction verification process. Furthermore, reference states are not consumed when the transaction is committed to the ledger but they are checked for “current-ness”. In other words, the contract logic isn’t run for the referencing transaction only. It’s still a normal state when it occurs in an input or output position. (see https://docs.corda.net/api-states.html#reference-states for more details)

To represent Reference states in a State Evolution diagram the state should be shaded out and be marked with 'Ref'. for example, the diagram below shows a Reference state being used in a Transaction where the AgreementState must be present for the 'SomeTranstion' transition on 'SomeOtherState' to be valid:



.. image:: ../resources/views/CDL_SE_Reference_state.png
  :width: 60%
  :align: center





---------------------
Explicit Transactions
---------------------

Each transition arrow in the State Evolution view implies a Corda transaction, this makes the view less cluttered as each state doesn't have to be represented as an output state in one transaction then again as a input state in another transaction.

However, sometimes we want to explicitly show a transaction with input and output states. For example, if want to show how two or more state machines interact with each other. This becomes particularly important when scaling complexity and designing for Privacy.

Let's assume we have a BillingState that needs to be included in the AgreementState's AgreeDeal and UpdateDeal transitions. To show that the two State Machines (AgreementState and BillingState) come together in a single transaction we will introduce a Transaction box which contains the explict transaction we want to show:


.. image:: ../resources/views/CMN2_SE_Transaction_box.png
  :width: 100%
  :align: center


1. Transaction box

  The transaction box indicates that these two transition occur in the same transaction. Note, when analysing if this is a valid transactions the Constraints from both State Machines will need to be considered.

2. Billing States

  The transition from the billing state is also show in the transaction box.

3. Duplicate States

  To avoid overlapping boxes we sometimes have to represent a State in two places (first as an output, secondly as an input). To do this we duplicate the State and use a thick arrow labelled 'consumed in'. If the State is only being shown in one transaction box then this is not necessary.

---------------------
Branching Transitions
---------------------

Sometimes a transition results in more than one output state, we can show this by spliting the transition arrow, for example in this extract from the billing pattern:



.. image:: ../resources/views/CDL_SE_Branching_transition.png
  :width: 60%
  :align: center
