================
Unknown Commands
================

Contract.verify does not handle all Commands and does not fail if it encounters an unknown command

The recommended approach is to handle all possible Commands in contract.verify, and to add an else branch that fails.

Also, to use the available utility methods like:  ``tx.commands.requireSingleCommand``, to make sure there's no ambiguity.



This protects against an attack where a node tries to bypass all checks by somehow abusing the Commands.

E.g.: The MyCash.verify logic does not fail when it encounters an unknown command. A malicious node could use a command from a different contract, and manage to bypass all the cash checks, thus being able to invent arbitrary MyCash states.



This is a good example on how to implement a secure Contract.verify: https://github.com/corda/corda/blob/master/finance/src/main/kotlin/net/corda/finance/contracts/CommercialPaper.kt#L106
