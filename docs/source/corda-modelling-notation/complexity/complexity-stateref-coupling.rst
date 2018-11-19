------------------------------------------
Coupling to a State Instance via StateRefs
------------------------------------------

In some cases, a State will need to hold a reference to another specific instance of a state on the ledger. It can do this by including the StateRef in its properties.

The StateRef consists of the hash of the transaction which created the referenced State and the index of the state in the transaction’s outputs, hence from the StateRef any previously state that has been committed to the ledger can be reference. Note, it doesn’t matter if the state is consumed or unconsumed.

If the requestor doesn’t have the state in its vault, a flow will need to be implemented to get the state from a party who does have the state.

This pattern may be useful when a piece of reference data, controlled by another entity needs to be tagged on to the state.
