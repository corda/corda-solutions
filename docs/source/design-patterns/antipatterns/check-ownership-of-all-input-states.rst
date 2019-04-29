===================================
Check ownership of all input states
===================================

We have to be very careful when implementing a CorDapp and developers seem to forget dealing with this particular attack vector:

We should always check if input states provided by our counter parties (all of them) in a transaction are NOT owned by our signing key. This should be done at the CorDapp level.

If we don’t check for key ownership, a malicious user might just add one or more unconsumed states we own (i.e., found via graph tracing or if we've transacted before) and because we sign the full transaction, we effectively sign for these states as well. In theory, all of the input states might be ours and we wouldn’t know.

As an example, imagine a currency exchange trade, where Alice wants to convert $ for £ by transacting with Trudy.

For instance, Alice will send a Cash state of $20 to Trudy and Trudy will send a state of £15 to Alice.

Let's assume that Alice also happens to have another state of £14 and Trudy knows its reference state.

Instead of using her own states, Trudy will use two input states: Alice's StateRef of 14£ along with another one that Trudy really owns of £1.

Eventually the transaction includes 3 input Cash states

 1. a $20 owned by Alice
 2. a £1 owned by Trudy
 3. a £14 owned by Alice

If Alice won't check that the £14 state belongs to her, then the transaction will be normally signed by both of them... and because these two keys can satisfy the transaction signing requirements, it can be sent for notarisation.
As a result, Trudy managed to exchange £1 for $20.

Note1: A solution to the above is not always straightforward, especially when at least one of the assets is owned by a multi-entity composite key (or is encumbered).

Note2: If we want to solve this at the Corda level (especially for cases of composite/complex ownership), we should introduce component-visibility Metadata to every signature.

Note3: Using one-time anonymous keys (for assets) in every transaction is also a good strategy, but we can only recommend it, as we can't really enforce it (and we've seen this happening with Bitcoin as well - i.e., non-profit organisations publishing a single address to receive donations).
