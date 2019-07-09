Corda Kill Flow Tool 
====================

The followin is an example of how to kill a flow in Corda using cordapp-example on Corda Open Source 4.1

By modifying the basic example flow to have the flow responder never respond, it was possible to demonstrate the new "flow kill" functionality.

The following steps are required:

- Start a flow which never ends
- Ensure there is a flow checkpoint
- Kill the open checkpoint
- Ensure there are no more remaining checkpoints

Demo
----

.. parsed-literal::
  > flow start ExampleFlow iouValue: 1000, otherParty: PartyB

.. parsed-literal::
   > Tue Jul 02 17:27:43 EDT 2019>>> flow start ExampleFlow iouValue: 1000, otherParty: PartyB
   > ✓ Starting
   > ✓ Generating transaction based on new IOU.
   > ✓ Verifying contract constraints.
   > ✓ Signing transaction with our private key.
   > ▶︎ Gathering the counterparty's signature.
   >     Collecting signatures from counterparties.
   >     Verifying collected signatures.
   >     Obtaining notary signature and recording transaction.
   >     Requesting signature by notary service
   >     Requesting signature by Notary service
   >     Validating response from Notary service
   >     Broadcasting transaction to participants
   >     Done

.. parsed-literal::

   > run stateMachinesSnapshot
   > - id: "786903da-d949-4cdc-af5d-e3a96778175f"
   >   flowLogicClassName: "com.example.flow.ExampleFlow$Initiator"
   >   initiator:
   >   username: "user1"
   >   progressTrackerStepAndUpdates:
   >   snapshot: "Gathering the counterparty's signature."
   >   updates: "(observable)"
   >   invocationContext:
   >   origin:
   >   actor:
   >   id:
   >   value: "user1"
   >   serviceId:
   >   value: "NODE_CONFIG"
   >   owningLegalIdentity: "O=PartyA, L=London, C=GB"
   >   trace:
   >   invocationId:
   >   value: "6a530c3c-ed70-43be-adc2-c4f594b1e8f9"
   >   timestamp: "2019-07-02T21:38:17.761Z"
   >   sessionId:
   >   value: "16eb884a-fea2-4e36-9cf6-3a04c209d995"
   >   timestamp: "2019-07-02T21:38:03.944Z"
   >   actor:
   >   id:
   >   value: "user1"
   >   serviceId:
   >   value: "NODE_CONFIG"
   >   owningLegalIdentity: "O=PartyA, L=London, C=GB"
   >   externalTrace: null
   >   impersonatedActor: null

.. parsed-literal::
   > flow kill 786903da-d949-4cdc-af5d-e3a96778175f
   >   [ERROR] 17:39:38-0400 [Node thread-1] corda.flow.processEventsUntilFlowIsResumed - Flow interrupted while waiting for events, aborting immediately {actor_id=user1, actor_owning_identity=O=PartyA, L=London, C=GB, actor_store_id=NODE_CONFIG, fiber-id=10000001, flow-id=786903da-d949-4cdc-af5d-e3a96778175f, invocation_id=6a530c3c-ed70-43be-adc2-c4f594b1e8f9, invocation_timestamp=2019-07-02T21:38:17.761Z, origin=user1, session_id=16eb884a-fea2-4e36-9cf6-3a04c209d995, session_timestamp=2019-07-02T21:38:03.944Z, thread-id=154}
   > Killed flow [786903da-d949-4cdc-af5d-e3a96778175f]
