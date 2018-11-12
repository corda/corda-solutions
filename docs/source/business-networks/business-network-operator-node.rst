Business Network Operator Node
==============================
Within a Business Network there exists the need for a trusted entity to provide specific services to the Business Network participants. This entity is known as the Business Network Operator Node.  

A business network operator (BNO) node provides the functions and services required to enforce the agreed network
policies and support its ongoing operation and evolution. The BNO node is a network participant with its own
identity and exposes its services via standard Corda APIs. However, generally speaking, the BNO does not participate in business transactions which are conducted directly between network users. This arrangement is depicted in the following diagram:

.. image:: resources/bno1.png
   :scale: 100%
   :align: center

Business Network Operator Services
----------------------------------
The set of services provided by a business network operator node vary by application. The following sections discuss typical services that may be required:

Membership Management
^^^^^^^^^^^^^^^^^^^^^
In addition to the assignment of a base identity to a Corda node that ensures each node across all business networks have a unique identity, each business network performs its own deeper membership management process, e.g., registration, licensing, and KYC/AML checks. While the exact requirements for each business network are governed by the network policies, the process of allowing nodes to join and transact on a network will be performed by the BNO node.

For an example membership management service see [link].

Master Data Management
^^^^^^^^^^^^^^^^^^^^^^
A common requirement for business networks is the need to maintain a set of shared master data that pertains to the application domain and made available to all business network participating nodes.  This data may be served via an API,  messaging system, or stored on-ledger, and governed by one more contracts.

Authorisation
^^^^^^^^^^^^^
Depending on the network policies, certain activities such as vault synchronisations or upgrades may require authorisation from the business network operator node.

Monitoring & Reporting
^^^^^^^^^^^^^^^^^^^^^^
For commercial, operational or regulatory reasons it is often a requirement to monitor and/or report on network level metrics.  For example, an operator may want to monitor network health by tracking operational metrics such transaction volumes and latency.  It may also choose to bill its members (periodically or on-demand) by tracking transactions across the network. The network may be designed to reveal as much or as little about the transactions as appropriate.

Announcements & Signaling
^^^^^^^^^^^^^^^^^^^^^^^^^
Certain network level events such as planned maintenance, outages and upgrades must be communicated to all network users.  In many cases, traditional communications channels may suffice but in some cases it may be appropriate to use a BNO service to distribute such information such that it can be integrated into the application itself.

Software Distribution
^^^^^^^^^^^^^^^^^^^^^
Although distribution of CorDapp jars and other shared dependencies may be managed via traditional deployment software tools, it may be appropriate to integrate this into the network itself.
