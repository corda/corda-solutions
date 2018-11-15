------------------------------
Corda Modelling Notation Views
------------------------------

This section gives an example of each CMN view, see the sections dedicated to each view for more details.

**Representing States**

The Ledger Layer views are mostly concerned with Corda States, or more precisely State-Contract combinations as you only define behaviour when you have both.

For the latest version of CMN we have standardised the representation of a Corda State across all the various views. The base representation of a Corda State is as follows and will be used in all three of the Ledger Layer views:


.. image:: resources/CMN2_State.png
  :width: 40%
  :align: center


1)  State and status

  The type of State and the Status of the state, this defines the boxes

2) Properties

  These are the properties of the State whilst in the particular status. Not all Properties need to be shown, just the ones salient to the behaviour of the state.

3) Participants

  These are the participants in the state, this corresponds to the parties who the Transaction containing the State will be sent to. Note, if the Transaction contains multiple States the Transaction will be sent to the union of all the States' participants.




**State Machine View (Ledger Layer)**


This view is based on the concept of a Finite State Machine. It treats each Corda State as being able to be in a finite number of statuses, describes the allowed transitions between statuses and the additional constraints which restrict those transitions. The emphasis is in articulating all possible evolutions of a given Corda State, enabling reasoning about how undesirable transitions are prevented from occurring. It does not seek to show how a corda state is intended to evolve, only how it **can** evolve based on the constraints present in the State and Contract which governs it. For example:

.. image:: resources/CMN_example.png
  :width: 80%
  :align: center


**State Instance View (Ledger Layer)**

Although looking somewhat similar to the State Machine View, the States Instance View is instead a snapshot of a set of States on the ledger showing their statuses, relevant properties and how they are linked together. It represents a subset of the total ledger and is independent of how the states came to be on the ledger. It is not trying to communicate controls or transitions, it is a just point in time instance diagram.

.. image:: resources/CMN_Instance_view.png
  :width: 60%
  :align: center


**Business Process Modelling Notation (BPMN) View (Orchestration Layer)**

The BPMN view aims to describe the business process. From a CorDapp perspective the purpose of this view is to identify all the possible business events that result in the an update the ledger. For example:

.. image:: resources/CMN_BPMN.png
  :width: 60%
  :align: center

For each Business event that requires a Ledger update, there will be two further views, the Transaction Instance View and a Flow Sequence View.



**Transaction Instance View (Orchestration Layer)**

The Transaction Instance View shows the specific transaction that will be built for the business event. It needs to be compatible with the allowed transitions in the Transaction Layer View, but instead of representing all possible evolutions of a State, this is a specific instance of a Transition. for example:


.. image:: resources/CMN_Transaction_instance.png
  :width: 80%
  :align: center



**Flow Sequence View (Orchestration Layer)**

The Flow Sequence view shows how the Corda Flow framework is used to correctly form, agree, notarise and distribute the Transaction in the Transaction Instance View. For example:

.. image:: resources/CMN_Reduced_sequence.png
  :width: 80%
  :align: center


For each view see it's dedicated section for more details.
