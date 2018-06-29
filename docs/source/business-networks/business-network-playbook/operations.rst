O. Operations
-------------

This section explores the more detailed operational concerns. Given this is a living document, we fully expect
to expand this section even more as we learn through experiences.

O.1. Devise Node Configuration
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

- Node role: participant, network map, permission, notary, oracle, observer
- Node geographic location
- Node deployment host, i.e., on-premises, cloud (provider?), Corda Network?
- Corda version
- Data store
- Does the data store provide the HA/DR capabilities to meet the BN resiliency requirements?
- Is there sufficient storage space to meet the data retention requirements?
- Is the node host/container appropriately configured to satisfy the performance SLA for the expected workload?
- Does participant already have a Corda node to which new Cordapps may be deployed?

O.2. Notary Configuration
^^^^^^^^^^^^^^^^^^^^^^^^^

- Location? Is location important?
- Protocol type, e.g., RAFT, BFT?
- Validating vs non-validating?
- Introducing or retiring notary – in Corda, by leveraging Notary-change transaction, it is possible to switch
  notaries, effectively introducing a new notary or deprecating, and ultimately, retiring their use, as needed.

O.3. Oracle Configuration
^^^^^^^^^^^^^^^^^^^^^^^^^

- Is the Oracle source verified?
- What are its configuration requirements?
- What are its data sources?

O.4. Create change management procedures
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

- Create and validate the change management procedures to meet the policies established by BNG.
  E.g. establish procedure for changing the consensus protocol

O.5. Establish Operational Times
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

- Satisfy System Availability policies.

   - What are the availability requirements?
   - How much down time may be tolerated?
   - What are the normal service hours of the network?
   - How will service interruptions (planned/unplanned) be communicated?
   - How will service interruptions be conducted?

O.6. Establish monitoring metrics
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

- What node metrics/alerts are required?
- What network metrics/alerts are required?
- What business metrics/alerts are required?

O.7. Define audit, report and documentation requirements
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

- What periodic network reports are required, and cadence?
- What periodic business reports are required, and cadence?
- What are the audit logging requirements?
- Might there be a minimal node certification required, as set by the business network governing body?  
- What documentation is needed?
- Who is responsible for creating/maintaining documentation?
- Where will documentation be available, accessible?

O.8. Define the network support parameters
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

- What are the first, second, third line support requirements, e.g., response times, escalation paths?
- Are there any support SLAs?

O.9. Define Data Retention and Pruning Procedures
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

- Based on the data retention guidelines, ensure the network has clear procedures to retain and prune data
  as needed, while being wary of data dependencies and maintaining the integrity of the network.
