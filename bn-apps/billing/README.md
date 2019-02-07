Billing Service
===============

*Contents of this article assume reader's familiarity with the concepts of *Business Networks*. Please see [Corda Solutions website](https://solutions.corda.net/business-networks/intro.html) for more information.*

*Billing Service requires Corda 4 as minimum version, as it heavily relies on reference states.*

Billing Service can be used for billing and metering on Business Networks. Billing Service has a notion of *Billing Chips* that can be included into Corda transactions that participants need to pay for. Billing Chips never cross a single transaction boundaries and hence never cause privacy leaks. All Billing Chips are attached to the parent *Billing State*, that accumulates total *spent* amount and can be safely reported back to the BNO without leaking private information about actual transactions where the Billing Chips have been used in. *Billing States Evolution* is depicted on the diagram below:

![Billing State Evolution](./resources/billing_state_evolution.png) 

*Please see [Corda Modelling Notation](https://solutions.corda.net/corda-modelling-notation/overview/overview-overview.html) for more information about modeling for Corda.* 

Billing workflow consists of the following steps:
1. BNO issues *Billing States* to their Business Network members. BNO can either pre-allocate an amount that a member can spent (bounded state), or leave it empty that would effectively allow a member to spent an unlimited amount of *Billing Chips* (unbounded state, can be used for transaction metering). *Billing States* can also contain and *expiry date*, after which it becomes unusable.
2. A member unilaterally (BNO's signature is not required) *chips offs* a *Billing Chip* from their *Billing State*. Chipping off increments *spent* amount of the associated *Billing State*. 
3. A member includes *Billing Chips* as inputs to the transactions that they need to pay for. Paid-for transactions never contain *Billing States* as an inputs and hence *Billing States* don't carry any private transaction history. However, valid *Billing States* must be included as *reference inputs* to prevent *Billing Chips* from being spent for expired, revoked or returned *Billing States*.
4. In the end of the billing period the BNO requests members to return their *Billing States*. Members attach back all *unspent Billing Chips* to their *Billing States*, which decrements *spent* amount. After that, members *return Billing State* to the BNO. Returned *Billing States* and their *Billing Chips* can not be used to pay for transactions anymore.  
5. BNO bills members based on the reported *spent* amounts. After all obligations are settled, BNO unilaterally *closes* all returned states.
6. BNO can also unilaterally (member's signature is not required) *revoke Billing States* as a result of a governance action. Revoked *Billing States* and their *Billing Chips* can not be used to pay for transactions anymore.

*Billing State Machine* is depicted on the diagram below:

![Billing State Machine](./resources/billing_state_machine.png)

What Billing Service is **not**:
* Billing Service is not a tokens framework. The service was designed to solve billing and metering problems in particular and is not intended to be used beyond these areas.
* Billing Service doesn't solve settlement problem. Consider using [Corda Settler](https://github.com/corda/corda-settler) for settlement of obligations.

# How It Works

## DataModel

Data model is represented with `BillingState`, `BillingChipState` and `BillingContract` (please see (here)[https://github.com/corda/corda-solutions/blob/billing-service-implementation/bn-apps/billing/billing-contracts-and-states/src/main/kotlin/com/r3/businessnetworks/billing/states/BillingContract.kt]).

`BillingContract` governs evolution of both of the states. 

## Billing State Issuance

A `BillingState` can be issued via `IssueBillingStateFlow`. The flow is supposed to be called by BNO.

```kotlin
subFlow(IssueBillingStateFlow(
    party, // the party to issue the BillingState to
    1000L, // amount of chips to be issued. Set to 0L if you would like to enable unlimited spending.
    expiryDate // Java Instant that defines when the BillingState expires. Transactions involving billing states with the expiry dates set require time window to be provided. 
))
```

After the flow is executed `BillinState` will be stored in the vaults of BNO and the state owner. 

## Chip Off

`BillingState` itself should not be included as input into any paid-for transactions. Instead, `BillingState` owners should chip off `BillingChipStates`, which then can can be included into actual business transactions.

Such mechanism prevents private business transaction history from being carried along with the `BillingState`.

To chip off a `BillingChipState` use `ChipOffBillingChipStateFLow`.

```kotlin
subFlow(ChipOffBillingStateFlow(
    billingState, // reference to the billing state to chip off from. If the issued is not 0, make sure that there is enough amount left to satisfy chip-off demand. 
    10L, // actual amount of the chip off. ChipOffBillingStateFlow can chip off multiple BillingStateChips in one go. All chips 
        // will have the same amount. 
    3, // number of BillingChipState to chip off. The total chip-off amount will be equal to numberOfChips * chipOffAmount 
    60.seconds // time tolerance for the transaction time window. Should be provided only if the billingState has an expiry date
))
```

## Using Chips

To get chipped off states fom the vault use `MemberDatabaseService`:

```kotlin
class MemberDatabaseService {
    // returns BillingState by linear id
    fun getBillingStateByLinearId(linearId : UniqueIdentifier) : StateAndRef<BillingState>?
    // returns all BillingState for the issuer 
    fun getBillingStatesByIssuer(issuer : Party) : List<StateAndRef<BillingState>>
    // returns all unspent chips for the billing state 
    fun getChipsByBillingState(billingStateLinearId : UniqueIdentifier) : List<StateAndRef<BillingChipState>>
    // returns all unspent chips for the issuer 
    fun getChipsByIssuer(issuer : Party) : List<StateAndRef<BillingChipState>> 
}
```

Unspent billing chips can be included as inputs to actual business transactions. To do that you need:
1. When building transaction, each participant should include their `BillingChipStates` as inputs.
2. Include `BillingStates` for the input chips as reference input.
3. Add `UseChip` command for each of the paying participant.
4. Add logic to your contract that verifies that each participant to the transaction has included enough chips as transaction inputs.

