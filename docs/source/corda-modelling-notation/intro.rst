Abstract
========

As CorDapps become more complicated there is a need for CorDapp analysts, designers and developers to be able to document and reason about CorDapp designs.

This paper proposes Corda Modelling Notation (CMN), a bespoke modelling notation to represent CorDapp designs.

When modelling we can consider Corda as two distinct layers:

1)	Ledger layer:

The Ledger layer provides the Distributed Ledger guarantees over common data and common processing logic. It includes the Corda States and the Corda Contracts which govern the evolution of those States through Transactions which update the ledger.

In CMN, The Ledger layer is modelled by the State Machine View, which is a repurposing of UML Finite State Machines. For example:

.. image:: resources/CMN-example.png
  :scale: 100%
  :align: center


.. toctree::
   :maxdepth: 1

   abstract
