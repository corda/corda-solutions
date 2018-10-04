=================================
Ledger Layer – State Machine View
=================================

In this discussion we will use the following definitions:

 * **State:** A class or object which inherits from ContractState interface in net.corda.core.contracts. In the training materials, this is what is referred to as a State.
 * **Ledger state:**	This is the sum total of all the unconsumed ContractStates
 * **Status:** (of a State)	A snap shot of the potential values of the properties of a State.
 * **Contract:**	A class or object which inherits from Contract in net.corda.core.contracts. This defines the set of constraints which operate over the States in a Transaction.
 * **Transaction:**	A Corda transaction, the mechanism by which the Ledger state is changed.

---------------------------------
Analogy to a Finite State Machine
---------------------------------

The Corda Ledger consists of the totality of the unconsumed States in the Compatibility Zone.
From the point of view of a Party on the Network, the ledger consists of all the States in the Party’s own vault. The Ledger can only evolve by the consumption of current States and the creation of new States in a valid Corda Transaction.

The Contracts attached to the Corda States impose a set of constraints which need to pass in order for the transaction to be valid. The constraints imposed by the Contract will depend on the Status of the consumed (input) and created (output) States in the transaction. For example, if a State has a status of ‘Closed’ it may not be allowed to be an input in a ‘Transfer’ transaction.

So, we have the Ledger State, then a set of allowed transitions to get to the new Ledger state of the ledger. This is similar to the idea of a Finite State Machine which forms the basis of this view.

However, modelling the entire Corda Ledger, even from the perspective of one CorDapp, as one big Finite State Machine is not practical. Instead we will model the behaviour of a single State. Later we will look at how different states types and different instances of the same State type can interact with each other.

UML (Unified Modelling Language) provides a notation for modelling Finite State Machines: https://en.wikipedia.org/wiki/UML_state_machine. The UML State Machine representation is the starting point for the Corda State Machine View, although there are some required modifications.

-----------
Basic model
-----------

The State Machine view aims to map out all of the possible ways that a State can evolve under the constraints imposed by its associated Contract.

Let’s take a simplified example:

.. image:: resources/CMN_example_FSM.png
  :width: 80%
  :align: center

1. Scope:

  Defines the State which is being modelled and the Contract which is constraining the evolution of the State. These must be defined as a pair, if you change either the State or the Contract then the possible statuses and the constraints over the transitions will change and hence the model will also change.

2. Status:

  A State can be in potentially many different Statuses. Status could be defined by a field called ‘Status’ or more generally a combination of the values of the properties of the State.  Not all possible statuses need to have their own box, similar statuses should be grouped such that all possible statues in the group don’t change the constraints applied. So, if a State has a possible status ‘Banana1’ and ‘Banana2’ but both have the same constraints, there is no need to have separate boxes on the diagram, both statues will behave in the same way.

3. Command:

  From a particular Status there may be many permitted transitions. Corda Commands parameterise and describe specific transitions and allow different constraints to be applied depending on the transaction Command.

4. No State:

  Indicates that there is no State (of this State type) at the beginning of this transition.

5. Potential Transactions

  Each of the transitions can, but not necessarily will, be enacted as a Corda transaction. The Status at the start of the arrow is an input state and the status at the end of the arrow is an output state. For the transition from Draft to Agreed, the transaction instance would look as follows:

.. image:: resources/CMN_Transaction_equivalent.png
  :width: 60%
  :align: center



Note that this is a subtly different view, the transaction instance shows one particular transition, the State Machine View shows all potential usages of the state in a transition. This is important as the state machine view enables the user of the model to reason about all possible usages, not just a selected subset of usages that are intended as part of the CorDapp design or are explicitly built in the flows.


-----------------------
Introducing Constraints
-----------------------

By default, Corda allows any transaction that is not explicitly disallowed. The Code to implement contract constraints is placed in the Contract’s verify() method. If you have a State with a Contract with an empty verify() method, with the exception that the input states must be unconsumed, there is no restriction over the composition of a transaction using those states.

To have a useful CorDapp we need to impose constraints over how the States are allowed to evolve. There are multiple types of constraints which we may want to impose on a State and a transaction involving the State, the modelling notations needs to reflect these.

It should be possible to reason that undesirable transitions are not permitted from the constraints in the model. It is envisaged that this will be important tools for audits and security reviews.

We will build up the types of constraints and show how they are represented in the modelling.

-----------------------
Transitions Constraints
-----------------------

The first type of constraint is the allowable transitions as denoted by allowable Commands

If you follow the diagram, we can see that when an agreement is in Draft, it only has two valid transitions, back to Draft via the Amend Command, or to Agreed via the AgreeDeal Command. It cannot move from Draft to Agreed.

The modelling assumption is that if the transition/ Command is not shown on the diagram, it should not be permitted to occur.

-----------------------
State Level Constraints
-----------------------

There will be some constraints over the form of an instance of a State that are independent of other components of a transaction. For these we need a more refined box to represent the State:

.. image:: resources/CMN_refined_box.png
  :width: 40%
  :align: center

1. Status:

  The top box describes the status of the State, it should be unique and describe all the properties which define the Box. If there is only one type of State in the diagram then the type of the State can be omitted.

2. Properties:

  The State may have many properties, this box describe a subset of those properties which are relevant to evaluating any constraints, State Level or otherwise, on the State.

3. State Level Constraints

  These are Constraints which operate on the State only, this might include internal consistency checks or valid value checks.

  Examples might be:

    -	If the state status is Draft, then Buyer, Seller and Goods must be populated, or
    -	The Seller and Buyer must not be the same Party

  It would not include constraints which need to look outside of the instance of the State, for example the input State and output state must be the same apart from property X, as this looks across two instances, even though they are the same type of State.

-----------------------------
Transaction Level Constraints
-----------------------------

Transaction Level constraints work over the whole of the transaction. Any information in a transaction can form the subject of the constraint.

.. image:: resources/CMN_Transaction_constraints.png
  :width: 80%
  :align: center

These could include:

  -	Permitted changes between input and output versions of the same type of State
  -	Requirements that a particular type of state is include in the transaction
  -	Requirements that a specified Command is included in the transaction

As the nature of the transaction changes based on the Command invoked, the transaction level constraints are modelled as being attached to the Command.

The total transaction level constraints in a given transaction is the union of the Transaction Level constraints attached to all Commands in the Transaction.

Note, Allowed Transitions, Required Signatures, Visibility constraints and multiplicity constraints are also type of Transaction Level constraints, but these have special importance so are shown separately to aid model understanding.

----------------------------
Required Signers Constraints
----------------------------

Required signers are denoted in brackets after the Command which defines the transition.

.. image:: resources/CMN_Required_signers.png
  :width: 80%
  :align: center

The restriction could be stated as a specific Party, but is more likely to be a role defined with in the state.

In more complicated examples, the constraint might be conditional, for example, two out of three of Parties A, B and C.

----------------------
Visibility Constraints
----------------------

A key differentiator for Corda compared to other Distributed Ledger Technologies is its peer to peer privacy so it is important to be able to model the Visibility Constraints of a CorDapp.

.. image:: resources/CMN_Visibility_constraint.png
  :width: 40%
  :align: center


Visibility constraints specify the restrictions on who should and should not receive a copy of the State.

Corda will distribute a copy of the transaction to the union of participants in all the states in the transaction. Visibility Constraints are restrictions on who should be in the total participants list for the transaction, not just the participant list for the State whose contract imposes the restriction.

Note, there is nothing stopping a Party distributing a copy of a transaction to any other party. Hence, where there is a constraint that a specified party cannot see a State, there is no absolute guarantee that they won’t see it through some other means, only that Corda will not automatically distribute the State to that Party.

------------------------
Multiplicity Constraints
------------------------

So far, we have modelled only a single State under a single Contract, however in production CorDapps there may be multiple interacting instances of the same State Type.


To describe this, we can add multiplicities to the transition’s arrows.

.. image:: resources/CMN_Multiplicity_constraints.png
  :width: 80%
  :align: center

In this example, we are stating that in a transaction there must be one and only one Agreement input State and one and only one Agreement output State. This doesn’t limit other types of State being in the transaction.

With fungible assets such as Cash you can have many cash input states and many cash outputs as long as the cash amounts balance across the transaction. This would be expressed as 1..n going to 1 ..m on the pay transition:

.. image:: resources/CMN_Cash_example.png
  :width: 80%
  :align: center

-------------------
Mapping to the Code
-------------------

Constraints are implemented in the Corda Contract associated with the Corda State.

The Contract verify() method’s behaviour is usually parameterised on the Command usually via a Kotlin ‘when’ statement (or a ‘switch-case’ statement if you are using Java). State level constraints are independent of the Command used and can be applied outside of the ‘when’ statement. Transaction Level Validations, including Required signers, Visibility constraints and Multiplicity constraints are specified within the ‘when’ branch corresponding to the command.

For example:

.. code:: kotlin

    class AgreementContract : Contract {

        interface Commands : CommandData {
            class Draft : TypeOnlyCommandData(), Commands
            class Amend : TypeOnlyCommandData(), Commands
            class AgreeDeal : TypeOnlyCommandData(), Commands
            class Update Deal : TypeOnlyCommandData(), Commands
            class Cancel : TypeOnlyCommandData(), Commands
        }

        override fun verify(tx: LedgerTransaction) {

            requireThat {

                *** State Level Constraints, ie Constraints which are independent of the Command ***

            }

            val command = tx.commands.requireSingleCommand<MyContract.Commands>()

            when (command.value) {
                is Commands.Draft -> requireThat {

                    *** Constraints applicable for the Draft transition ***

                }
                is Commands.Amend -> requireThat {

                    *** Constraints applicable for the Amend transition ***

                }
                is Commands.AgreeDeal -> requireThat {

                    *** Constraints applicable for the Agree transition ***

                }

                is Commands.UpdateDeal -> requireThat {

                    *** Constraints applicable for the Agree transition ***

                }
                is Commands.Cancel -> requireThat {

                    *** Constraints applicable for the Cancel transition ***

                }

                else -> {

                    throw TransactionVerificationException
                }
            }
        }
    }

Allowed Transitions are managed in this case by the ‘when’ statement, if the Command is not in a valid ‘when’ branch then the contract throws an error. However, this is a simplified example which assumes one Command in the transaction, the logic would be more complicated when multiple commands can be present.

-------------------
Putting it together
-------------------

We can see the modelling structure and each of the types of constraints in this example of an Agreement State:

.. image:: resources/CMN_Agreement_example.png
  :width: 100%
  :align: center
