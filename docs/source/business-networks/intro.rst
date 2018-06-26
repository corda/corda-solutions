Corda Business Networks
=======================
In 2016, the design concepts for Corda were described for the first time in Mike Hearn's `technical whitepaper
<https://docs.corda.net/_static/corda-technical-whitepaper.pdf>`_.  It defined a general platform for decentralised application development.  While the paper described the concept of "CorDapps" (Corda Distributed Applications) and how these might be constructed, it did not attempt to provide much more detail.

Building and managing distributed applications is more involved than just creating software. In a CorDapp, information and/or assets are exchanged, or transacted, between participants because they represent something meaningful to each participant. But how is the meaningfulness of the exchanged information determined, on what basis, and by whom?  Such are the considerations we aim to understand.

Our problem is not particularly new.  There are many examples of companies and consortiums predating blockchain or
DLT (Distributed Ledger) technology that have brought people together to transact value.
The blockchain vision enabled by Corda is different because it was designed to eliminate many of the
problems inherent in earlier approaches.

First, let us give our problem a name:  We have a group, or network, of independent parties that want to
use Corda to transact something of value, and as we're transacting something valuable then this almost inevitably implies a commercial, or business purpose.  As such, we can talk about a "Business Network".

.. toctree::
   :maxdepth: 1

   what-is-a-business-network
   how-do-business-networks-start
   roles-and-responsibilities
   business-network-operator-node
   interoperability
   business-network-playbook/intro
