Corda Enterprise Network Architecture
=====================================

- Corda uniquely enables P2P Corda Networking within security constraints of corporate networking architectures. 
- Restricts access to Corda node from the internet only to nodes with valid identity certificates.
- Deployed in DMZ
- Terminates TLS Connections
- Does not connect into the internal network, connection initiated from the Node. 

Consideration for Corda Deployment in Bank Environment
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Deploying on-premises within a bank presents a number of specific security challenges There is typically a strongly-layered approach to defense. At a minimum there will be 3 separate zones:

- DMZ (directly addressable from the Internet)
- Application (not addressable from the Internet)
- Data (no access to the Internet).
- DLT/Blockchain platforms present a significant new deployment challenge because they exhibit behavior similar to a peer-to-peer network; they both initiate and terminate connections with external hosts on the same port. This type of behavior is new in many enterprise deployments. This document summarizes the thinking around deploying R3 Corda within a Bank environment and provides detailed deployment recommendations. 

It is important at the outset to understand the goal of your deployment, and take into consideration the internal infrastructure stakeholders for example:

- Systems Administration
- Security
- Network
- Enterprise Architecture
- In addition it is important to understand your deployment environment i.e. will it be On Premises or Cloud based? Corda is Cloud agnostic and can be deployed as required in Azure, AWS, Google and other market offerings.  

Understand Corda comms protocol usage:

- HTTPS for Doorman/Network Map
- P2P AMQP/TLS for Node-to-Node interaction
- RPC for client application to node interaction
- SSH for node administration

Understand Corda Firewall components:

- Node (Flow Worker)
- Bridge
- Float

Determine appropriate operating mode:

- UAT
- PROD

