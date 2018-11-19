===================
Managing Complexity
===================

So far, we have focused on modelling CorDapps which have a single State type. However, production CorDapps are likely to have more than one type of Corda States interacting with each other.

In order to cope with the increased complexity that multiple state types introduce, we can use the concepts of high cohesion and low coupling. From Wikipedia:

  *	Cohesion refers to the degree to which the elements inside a module belong together.

  *	Coupling is the degree of interdependence between software modules

We can consider each of the Corda States represented by its own State Machine as a module. Hence, to achieve high cohesion the functions performed by the State should all be related. The challenge is to work out how to couple the modules.

There are a number of potential couplings:

.. toctree::
   :maxdepth: 1

   complexity-flow-coupling.rst
   complexity-commands-coupling.rst
   complexity-stateref-coupling.rst
   complexity-linearid-coupling.rst
