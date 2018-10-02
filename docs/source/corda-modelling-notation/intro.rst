========================
Corda Modelling Notation
========================

As CorDapps become more complicated there is a need for CorDapp analysts, designers and developers to be able to document and reason about CorDapp designs. This paper proposes Corda Modelling Notation (CMN), a bespoke modelling notation to represent CorDapp designs.

This page gives an overview of each of the CMN views, the sub pages build up each of the Views step by step.

Modelling Cordapps
------------------


When modelling we can consider Corda as two distinct layers, the Ledger Layer and the Orchestration Layer:

.. image:: resources/CMN_Cordapp_split.png
  :width: 60%
  :align: center


The Ledger layer
~~~~~~~~~~~~~~~~

The Ledger Layer provides the Distributed Ledger guarantees over common data and common processing logic. It includes the Corda States and the Corda Contracts which govern the evolution of those States through Transactions which update the ledger.

In CMN, The Ledger Layer is modelled by the **State Machine View**, which is a repurposing of UML Finite State Machines. For example:

.. image:: resources/CMN_example.png
  :width: 80%
  :align: center

The primary purpose of the State Machine view is to describe and reason about the possible evolutions of a state and the constraints over those evolutions

There is also the **States Instance View** which, although looking somewhat similar, is a snap shot in time of a set of States in a particular status together with how they are linked together.


.. image:: resources/CMN_Instance_view.png
  :width: 60%
  :align: center

The Orchestration layer
~~~~~~~~~~~~~~~~~~~~~~~

The Orchestration Layer coordinates the communications between parties, builds proposed transactions, provides APIs to trigger actions on the ledger.

An important distinction from the Ledger layer is that the Orchestration layer is only a suggested set of functionality distributed from the CorDapp developer. A Party operating on the network can, and likely will, rewrite their Orchestration Layer to implement bespoke functionality. Any Logic which must be guaranteed between the Parties must be encoded in the Ledger Layer.

The Orchestration layer is modelled by three views.

**Business Process Modelling Notation (BPMN) View** to identify business events that require a Corda Transaction:

.. image:: resources/CMN_BPMN.png
  :width: 60%
  :align: center

**Transaction Instance View** to represent the Corda Transaction for each business event:

.. image:: resources/CMN_Transaction_instance.png
  :width: 80%
  :align: center

**Flow Sequence View** to represent the Corda Flows which build and agree the Corda Transaction for the business event:

.. image:: resources/CMN_Reduced_sequence_flow.png
  :width: 80%
  :align: center


Modelling Complexity in CorDapps
--------------------------------

This paper also considers how to scale complexity in a CorDapp by applying the concepts of high cohesion / low coupling to CorDapp designs.

It proposes splitting the design into modules where individual Corda State types provide related functionality (high cohesion) which are then (loosely) coupled together by one of four mechanisms:

  1)	Flow level coupling
  2)	Commands coupling
  3)	Coupling to a State instance via StateRefs
  4)	Coupling to a state’s evolution via Linear Id
