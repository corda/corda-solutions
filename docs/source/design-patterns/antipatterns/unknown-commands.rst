================
Unknown Commands
================

Contract.verify does not handle all Commands and does not fail if it encounters an unknown command

The recommended approach is to handle all possible Commands in contract.verify, and to add an else branch that fails.

Also, to use the available utility methods like:  ``tx.commands.requireSingleCommand``, to make sure there's no ambiguity. However, this will not be suitable if there needs to be multiple Commands in one transaction, in that case use ``tx.commandsOfType<Commands>()`` where Commands is defined in the Contract. This will isolate the Commands relevant to a particular Contract.


This protects against an attack where a node tries to bypass all checks by somehow abusing the Commands.
