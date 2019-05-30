AWS HA Deployment Overview
============================

This guide is designed to provide detail on how to deploy Corda Enterprise in a HA environment in AWS.  

The guide is a supplement to detail on Hot/Cold deployment which can be found here: https://docs.corda.r3.com/hot-cold-deployment.html. 

The image below illustrates the environment that will result from following the guide. There will be two Corda Enterprise nodes, one active and the other inactive. Each node will represent the same legal identity inside the Corda network. Both will share a database and a network file system.

.. image:: ./resources/ha-overview.png
   :scale: 50%


In order to deploy Corda Enterprise in HA the following AWS resources are required:

- AWS Security Group with default Inbound/Outbound TCP, HTTP Access Rules
- AWS Network Load Balancer to present single IP to P2P & RPC. Load balancer uses Listener rules to route requests to targets.
- Listeners to check for connection requests to allow P2P, RPC & HTTP to Corda Nodes  
- Target Group consisting of of 2 VM’s for the LB to target 
- Elastic IP Addresses allocated to VM's & Load Balancer to ensure static IP and DNS names for VM's. 
- Static public Load Balancer IP addresses & DNS names for Appplication Server/CorDapp access. 
- AWS Elastic File System set up as a File Service which acts as a shared mount point on both Primary and Backup VM. This file system will have a Public IP address and can be mounted on both VM's on startup.
- Oracle, Postgres or SQL Database which will be shared by the Corda Enterprise Nodes on both Primary and Backup VM's
- Network Security rules which, when configured, will allow P2P, RPC & HTTP traffic from outside the Azure network to the Corda Nodes on Primary and Backup VM's

It is important to note at the outset of deployment that Corda Enterprise need a direct network connection to other Corda Enterprise Nodes in the network. This means that the entry in the Network Map for every Corda Node must be a static Public IP address or Public DNS name. 
