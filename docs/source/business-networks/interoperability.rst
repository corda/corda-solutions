Interoperability
----------------
Our vision for Corda is a platform that allows the universal interoperability of public networks but with the privacy of private networks.  The Corda Network provides a global and openly governed environment with strong identity and privacy assurances and business networks provide the

However, our architecture supports

Since Corda nodes support the loading of multiple CorDapps, it is possible for a node to participate in multiple business networks simultaneously whilst still conforming to the policies and processes that each defines.

This is important as it facilitates atomic transactions across different asset types which is a core element of R3's overall vision.  The following diagram illustrates this:

.. image:: resources/interop.png
   :scale: 80%
   :align: center


Here we see a three interoperable business networks with assets and contracts defined in three separate node plugins (CorDapps).  Alice, Carl, Demi and Clara are participants in multiple networks whilst the remaining parties are only involved in one.

We can make the following observations:

 - The networks are (potentially) independently operated and governed
 - Each network would enforce it's own access control policies and processes
 - Multi-assets transactions are validated by contracts in multiple CorDapps

TODO: we need more detail on this subject
