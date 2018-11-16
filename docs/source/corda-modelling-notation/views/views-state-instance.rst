========================
States Instance (Ledger)
========================

The States Instance View is a snap shot in time of a set of States in a particular status together with how they are linked together. It is a Ledger Layer view as it represents a subset of the ledger and is independent of how the states came to be on the ledger. It is not trying to communicate controls or transitions, it is a just point in time instance diagram.

An instance diagram for our example might look like this:

.. image:: ../resources/CMN_Instance_view_arrows.png
  :width: 90%
  :align: center


The diagram is based on states using a similar representation to the State Machine View, however there are some important differences:

1. State Ids

  As we are talking about instances of states, we are likely to need to add in identifiers for the instance of the state, typically these would be the Linear Id of the state, but could also be the stateRef depending on how the states reference each other.

2. Properties

  These are similar to the properties in the state machine view but are more likely to hold specific values rather than just the property type.

3. Multiple instances of the same state/status

  The diagram has two boxes which are AttachmentStates, this is because there are two instances of the AttachmentStates, however these are both ‘Live’ Attachment states so the equivalent State machine view would only have one box.

4. No constraints

  The diagram is not trying to show constraints, hence the box for state level constraints is not in the state box

5. Participants not visibility constraints

  The visibility box is replaced by a participant’s box. Whereas the visibility box set out the constraints around acceptable participants in the transaction, the participants box shows actual participants on the state.

6. State references

  The diagram now shows references between states, the state box shows the reference and there is a dotted line between from the state holding the reference to the referenced state

7. Diagram can include any state instances

  The diagram can include any state instances which the drawer feels are relevant. In this diagram, we include the two BillingStates even though they are not referenced from the other states.
