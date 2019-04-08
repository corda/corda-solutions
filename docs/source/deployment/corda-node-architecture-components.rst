Architecture Overview
=====================

It is useful to take a high level perspective of the Corda components, especially where it comes to the various communication protocols that Corda employs in its operations. The diagram below illustrates the various communication protocols used by the Corda Node communicating with peers on the Corda Network.


.. image:: ./resources/overview.png
   :scale: 50%
   :align: center

Note that Corda Nodes communicate with each other using an asynchronous protocol, AMQP/TLS. The only HTTP communication is for the initial registration of each Corda Node, and for sharing of the Corda Node address locations by way of the Network Map. Each client application communicates with Corda Nodes using RPC calls. Also, the Corda Vault is a database that relies on JDBC connection from the Corda Node.

The salient points regarding hosting a Corda Node on-premises are:

- Corda uniquely enables P2P Corda Networking within security constraints of corporate networking architectures.
- Restricts access to Corda node from the internet only to nodes with valid identity certificates.
- Deployed in DMZ
- Terminates TLS Connections
- Does not connect into the internal network, connection initiated from the Node.

To aid deeper understanding, the diagram below illustrates in more detail the basic components that are typically deployed:

- Corda Enterprise Node
- Corda Enterprise Vault
- Corda Enterprise Firewall


.. image:: ./resources/nodebridgefloat_nbrs.png
   :scale: 100%
   :align: center


The diagram highlights that:

1. **CorDapps** are the functional aspect of Corda that allows Corda Nodes to reach agreement on updates to the **Vault** (DLT ledger/database) for given use cases.
#. Corda Nodes persist the shared results of CorDapps in a database (vault) using **JDBC**.
#. **Corda Nodes** communicate in peer-to-peer fashion using **AMQP/TLS**.
#. **Corda Firewall** is an optional reverse proxy extension of the Corda Node intended to reside in the DMZ, enabling secure **AMQP/TLS** interaction with peer Corda Nodes.
#. Client applications interact with Corda Nodes using **RPC/TLS**.
#. Administrators interact with Corda Nodes over **SSH**.
#. Corda Nodes attain an identity certificate via a Doorman service using **HTTPS**.
#. Corda Nodes learn about other trusted Corda Nodes and their addresses via a Network Map service using **HTTPS**.
#. Corda Nodes check a Certificate Revocation List using **HTTPS**.

The Corda Firewall is actually made up of two separate programs, called the Bridge and the Float. These handle outbound and inbound connections respectively, and allow a node administrator to minimise the amount of code running in a network’s DMZ.

The primary function of the Corda Firewall is to act as an application level firewall and protocol break on all internet facing endpoints.

The Float is effectively an inbound socket listener which provides packet filtering and is a DMZ compatible component.  The Float exposes a public IP address and port to which other peers on the network can connect. This prevents the Node from being exposed to peers.

The Floats primary function is to bundle messages and send them to the Bridge across a DMZ internal firewall. The Bridge in turn runs some additional health checks on the message prior to sending to the Corda Node Artemis queue.

The Corda Node advertises the Float's Public IP address for P2P communications, as this is the IP address that is listening for peer Node communications. The Float's Public IP address must be configured on the outer Firewall such that peers can connect to it.

The Corda Node VM Public IP address is used for RPC client connections.
