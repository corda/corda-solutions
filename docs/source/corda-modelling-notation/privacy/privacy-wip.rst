
Privacy is one of the major selling points of Corda. Much is made of it's peer to peer nature and the fact that transactions are not broadcast to the whole of the Network. But there is an important subtlety here: for a transaction to be valid, Parties needs assurance of the provenance of it's input states, this often involves receiving transactions from the historic chain of states leading to the current transaction. Corda has built in functionality to resolve these historic transactions via the ResolveTransactionFlow(), however, care needs to be taken when designing CorDapps to ensure that resolution of a transaction doesn't lead to Parties obtaining historic information which they should not get visibility of, for example the transactions of their competitors.

There are mechanisms to preserve privacy such as Confidential Identities and Chain-snipping, but these need to be actively designed into CorDapps. A CorDapp designer needs to be confident in the privacy characteristics of the application. In order to be able to reason about privacy we need a mechanism for analysing Privacy in the context of Corda. The CMN privacy overlays aim to meet this need.







Privacy leak is when a Party gets to see something they shouldn't

Because by definition privacy is about seeing things which exist but a party should not see the analysis is backward looking.


the analysis starts from a point in a state/ states evolution

the analysis is from the point of view of a specific Party

The analysis seeks to show what a Party gains access to, and indicate whether that access is appropriate or not marked as a privacy leak

Multiple points of view can be layered on a diagram

It is often not possible to show a closed graph of state evolutions, or to show the full Corda Ledger, so there needs to be away to show the edges of the graph that is being considered.

Privacy chain in - this is where what happens to the state after the scope of the diagram is not shown.

Privacy Chain out - this is where what has happened to the state before the scope of the diagram is not shown.

Privacy Start - this is where a states evolution has come to an end. Hence it will not be involved in any more transactions

Privacy End - this is where a state evolution starts, because there are no prior states in the states evolution there is no need to consider privacy before this point.





Privacy Mapping

Privacy Start: The point from which there will be no further sharing of the state, hence privacy reasoning starts from this point.

Privacy In: Where the transaction will be used in future by a set of evolutions outside of this diagram

Privacy Out: Where the history of a state is available to a legitimate Party

Privacy Leak: where a transaction or the history of its input states are available to a non-legitimate Party

Privacy End: the issuance of the chain of states, hence the end of the backward looking privacy chain
