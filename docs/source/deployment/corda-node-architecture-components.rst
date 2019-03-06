Overview of Corda Node Architecture Components
==============================================

This diagram illustrates the communication protocols used by the Corda Node communicating with peers on the Corda Network.


.. image:: overview.png
   :scale: 100%
   :align: center

- Corda uniquely enables P2P Corda Networking within security constraints of corporate networking architectures. 
- Restricts access to Corda node from the internet only to nodes with valid identity certificates.
- Deployed in DMZ
- Terminates TLS Connections
- Does not connect into the internal network, connection initiated from the Node. 
