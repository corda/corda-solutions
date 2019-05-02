====================
Dual roles for nodes
====================


There are at least 4 different node types in Corda, including:

Regular transacting node
Validating Notary node
Non-Validating Notary node
Oracle node
Note: we might also have nodes who do not own any assets, but should sign as tx "approvers" (i.e., regulators/auditors etc).

We recommend against having a dual or multiple role (any combination of the above), especially if the same key(s) are used to sign for processes related to different roles.
For instance, if one node acts as an Oracle and a regular node at the same time and it uses the same legal identity key, the following attack vector is possible.

1. Oracles sign filtered transactions and they can only see the commands related to their business (i.e., a command including fx-rates).

2. If this Oracle-RegularNode entity has assets owned by the same key used for "oraclisation", then a malicious user might know some unconsumed states owned by this key (i.e., through graph tracing or if they've transacted in the past) and use them as input states in the oraclised transaction (and send them to keys controlled by the attacker).

3. Then, the Oracle will sign the filtered transaction, in which these "injected" states (owned by the Oracle itself) won't be visible.

4. The latter means that the Oracle blindly approved the contents of the transaction and will lose ownership of its assets after the transaction is notarised.

Note: the above attack (and its variants) can be performed against other role combinations as well.
