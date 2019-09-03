----------------------------
Managing Complexity with CDL
----------------------------

The Managing Complexity in CDL section considers how to scale complexity in a CorDapp by applying the concepts of high cohesion / low coupling to CorDapp designs.

It proposes splitting the design into modules where individual Corda State types provide related functionality (high cohesion) which are then (loosely) coupled together by one of four mechanisms:

  1)	Flow level coupling
  2)	Commands coupling
  3)	Coupling to a State instance via StateRefs
  4)	Coupling to a state’s evolution via LinearId
