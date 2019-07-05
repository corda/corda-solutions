===========================================
Reference States as a proof of current data
===========================================

WIP

References states can be used to prove that a state is current, even though that state is not consumed in the transaction.

For example, say the GBP-USD FX rate is represented by a DailyFXRateState. Every day the previous day's DailyFXRateState is consumed and replaced by a new DailyFXRateState containing the new rate.

Two counterparties want to perform a USD - GBP FX swap.

The transaction has:
 - 15 USD(owner A) -> 15 USD(owner B)
 - 10 GBP(owner B) -> 10GBP(owner A )
 - refstate( dailyFXRateState = 1.5 )

The swap Contract rules say that the transaction is not valid unless there s a current DailyFXRateState included in the transaction as a reference state.

If the transaction references the current (unconsumed) dailyFXRateState, then the transaction will verify.
If the transaction tries to reference yesterday's (consumed) DailyFXRateState the transaction will not verify and the transaction will fail.



Anti pattern:

Note, one possible application of reference states would be to reference a transferred Cash or asset state as proof of payment to allow a transfer to take place eg transfer some other assets. This is problematic as you have to be careful not to allow the same cash state to be referenced in multiple transactions, ie similar to double spending.

This can be partially mitigated by agreeing a specific pre-agreed reference to be added to the cash state, however a malicious actor could deliberately set up two transactions with the same required cash reference and commit a double spend.
