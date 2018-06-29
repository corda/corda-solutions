G. Governance
-------------

In this section we step through a series of important aspects that will form or contribute to a governance model.
As mentioned earlier, it may be worth reviewing examples of governance models. Ultimately, this section will help
us capture who our network members are, the core purpose of using the network, and the policy decisions needed to
operate it.

G.1. Describe Participants
^^^^^^^^^^^^^^^^^^^^^^^^^^

- What are the various types of network participants?

   - Asset issuer
   - Network participant (signer)
   - Corda node administrator
   - Identity verifier
   - Regulator
   - Auditor

- Service provider, e.g., oracle that provides trusted external data such as stock prices, currency rates, weather conditions at specific time points.
- Which participants require a node/identity?
- How many participants of each type will the network include?
- Define all the stakeholder roles from each organization that will be required for each participant to deploy a
  node and join a network, e.g, Procurement, IT Security, IT network, IT change management, IT App Dev team
  supporting the app portfolio related to the business network.

G.2. Define the Asset Model
^^^^^^^^^^^^^^^^^^^^^^^^^^^

- Provide the asset(s) definition and important elements that comprise the asset.
- Describe the asset(s) lifecycle from inception to end, and all the in-between possible lifecycle states.

G.2. Define the Business Process
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

- Articulate the business processes driving the asset(s) lifecycle among the participants in the target network.
- Identify which participants have authority to change the state of the asset(s).

G.4. Outline Legal and Regulatory Needs
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

- Identify legal and regulatory parties expected or required to be involved in the business network.
- Capture legal and regulatory requirements/boundaries.
- Assess and prepare for any intellectual property boundaries.

G.5. Establish the Network Financial Model
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

- What is the network commercial model?
- What are the operating cost factors?
- Is network for profit? Cooperative with shared costs?
- How will the costs be shared? Or how will fees be determined?
- What is the initial funding required to start? (procurement involvement?)

G.6. Establish Data Privacy Policies
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

- What data elements are considered private/sensitive?
- Who can create, modify, view which elements?
- Is data-at-rest encryption required?
- Are there jurisdiction laws governing the use, retention or exposure of data, e.g., GDPR?

G.7. Establish Data Retention policies
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

- Ensure clear guidance based on legal requirements for data retention needs.
- Is there a need to prune data that exceeds the data retention requirements?

G.8. Establish Data Resiliency and System Availability Policies
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

- What are the availability requirements?
- How much down time may be tolerated?
- Are there jurisdictional boundaries within which the data must remain?
- What is the recovery time SLA?

G.9. Assert Data and/or Processing Standards
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

We need to define how information, data, assets, etc., are represented within the business network.  In some cases
these will be proprietary representations, but in others we may wish to adopt national, international, or industry,
standards such as ISDA or ACORD.

- How will data be represented, and how should it be interpreted?

G.10. Establish Transaction Validation Policies
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

- Policies driving CorDapp Contract code.
- Policies related to asset model validations.
- Policies specific to each party’s independent validation.

G.11. Establish Dispute Management Policies
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Code is not law. Using blockchain does not replace the need for common sense dispute management. 
Arbitration steps will be required.

- How are exceptions managed at:

   - Human interaction level?  Who will comprise the Arbitration team?  Might there be a third party?
   - Business processing level?
   - Smart Contract level?  Is there a rollback where transaction is deemed invalid, or might there be some interim
     state recorded depending on the type of exception processing?  Are there or can there be aspects of
     arbitration designed into the Smart Contracts?
   - Consider cross-jurisdiction challenges in dispute management and arbitration.

G.12. Establish On-boarding Policies
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

- What processes are needed to add a new party?
- Any regulatory requirements for adding a new party, e.g., KYC, AML?
- What is the timing, the SLA requirement for on-boarding?

G.13. Establish Off-boarding Policies
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

- What processes are needed to remove a party?
- Any regulatory requirements for removing a party?
- What is the timing, the SLA requirement for off-boarding?
- Does the removal of the party create a data dependency problem that needs to first be resolved, e.g., asset
  ownership, historical chain-of-custody?

G.14. Establish Inter-Network Policies
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

There may be no need for inter-network interactions, but where they exist then we should give some thought
as to any policies around their use.

G.15. Define Performance SLAs
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

- Acceptable ranges for the network operating hours:

   - Throughput (average vs peak)
   - Latency

G.16. Establish Change Management Policies
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

- Establish rules for how to agree on policy changes described in this section?

G.16.1. Business Network Role Changes
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

- Establish rules for managing BN role changes, the network governing body, e.g., is there an election process?

G.16.2. Business Network Legal Agreement Changes
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

- Establish rules for managing BN legal agreement updates.

G.16.3. Corda Platform Change Management Rules
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

- Establish policies for agreeing on platform updates.

G.16.4. CorDapp Change Management Rules
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

- Establish policies for agreeing on Cordapp code deployment, testing, versioning, and general lifecycle management.
