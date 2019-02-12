-----------------
Why Model Privacy
-----------------

Privacy is one of the major selling points of Corda. Much is made of it's peer to peer nature and the fact that transactions are not broadcast to the whole of the Network. But there is an important subtlety here: for a transaction to be valid, parties needs assurance of the provenance of it's input states, this often involves receiving transactions from the historic chain of states leading to the current transaction. Corda has built in functionality to resolve these historic transactions via the ResolveTransactionFlow. However, care needs to be taken when designing CorDapps to ensure that resolution of a transaction doesn't lead to parties obtaining historic information which they should not get visibility of, for example, the transactions of their competitors.

There are design patterns to preserve privacy such as Confidential Identities and Chain-snipping, and in the future SGX has much to offer, but these need to be actively designed into CorDapps. A CorDapp designer needs to be confident in the privacy characteristics of the application. In order to be able to reason about privacy we need a mechanism for analysing Privacy in the context of Corda. The CMN privacy overlays aims to meet this need.

