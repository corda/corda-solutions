Billing Service
===============

*Contents of this article assume the reader's familiarity with the concepts of *Business Networks*. Please see [Corda Solutions website](https://solutions.corda.net/business-networks/intro.html) for more information.*

*Billing Service requires Corda 4 as a minimum version, as it heavily relies on the Reference States.*

*Please see* [Billing Demo App](./billing-demo) *for an example of how to integrate Billing Service into your CorDapp.*

Billing Service can be used for billing and metering on Business Networks. Billing Service has a notion of *Billing Chips* that can be included into Corda transactions which participants need to pay for. *Billing Chips* never cross a single transaction boundaries (when created, *Billing Chips* can be consumed in an exactly one transaction) and hence never cause privacy leaks. All *Billing Chips* are attached to their respective *Billing States*, that accumulate the total *spent* amount and can be safely reported back to the BNO without leaking the transaction history where the *Billing Chips* have been involved into. 

> We felt that there is a need for a dedicated billing pattern as many suggested approaches we have encountered leak privacy. For example if you were to use tokens to implement billing on Business Networks, then there would be no way for a member to report the actual spent amount, without leaking the transactions where the tokens have been used in. 

*Billing States Evolution* is depicted on the diagram below. The diagram is created in *Corda Modelling Notation*. Please see [this link](https://solutions.corda.net/corda-modelling-notation/overview/overview-overview.html) for more details.

![Billing State Evolution](./resources/billing_state_evolution.png) 

The billing / metering workflow consists of the following steps:
1. The BNO issues a *BillingState* to each of their Business Network members. The BNO can either pre-allocate an amount that a member can spent (bounded state), or leave the amount empty which would effectively allow the member to spent an unlimited number of *Billing Chips* (unbounded state, can be used for transaction metering). *Billing States* might include an *expiry date*, after which the states can not be used anymore.  *Billing States* may also include a externalId which can be used to differentiate amongst multiple active billing states for a member.
2. A member unilaterally (the BNO's signature is not required) *chips offs* one or multiple *Billing Chips* from their *Billing State*. Chipping off increments the *spent* amount of the associated *Billing State*. 
3. A member includes *Billing Chips* as inputs to a transaction he(she) needs to pay for. Paid-for transactions never contain *Billing States* as inputs and hence *Billing States* don't carry along any private transaction history. However, the valid *Billing States* must be included as *reference inputs* to prevent an expired, revoked or returned states from being used. Developers must define a logic in their contracts code that verifies that each of the required transaction participants had included enough of the Billing Chips as inputs.  
4. In the end of the billing period the BNO requests each BN member to return their *Billing State*. In response to that, the members attach back all *unspent Billing Chips* to the respective *Billing States*, which decrements the *spent* amount value and then *return Billing States* to the BNO. The returned *Billing States* and the associated *Billing Chips* can not be used to pay for transactions anymore.  
5. The BNO bills the members based on the reported *spent* amounts. After all obligations have been settled, the BNO unilaterally *closes* all returned states. 
6. The BNO can also unilaterally (a member's signature is not required) *revoke Billing States* as a result of a governance action. The revoked *Billing States* and the associated *Billing Chips* can not be used to pay for transactions anymore.

*Billing State Machine* is depicted on the diagram below. The diagram is created in *Corda Modelling Notation*. Please see [this link](https://solutions.corda.net/corda-modelling-notation/overview/overview-overview.html) for more details.

![Billing State Machine](./resources/billing_state_machine.png)

What Billing Service is **not**:
* Billing Service is not a tokens framework. The service was designed to solve the billing and the metering problems specifically and is not intended to be used outside of these areas. Consider using [Corda Tokens SDK](https://github.com/corda/token-sdk) as a general purpose tokens framework. 
* Billing Service doesn't solve the settlement problem. Consider using [Corda Settler](https://github.com/corda/corda-settler) for settlement of obligations.

# How It Works

## Data Model

The data model is represented with `BillingState`, `BillingChipState` and `BillingContract` [classes](https://github.com/corda/corda-solutions/blob/master/bn-apps/billing/billing-contracts-and-states/src/main/kotlin/com/r3/businessnetworks/billing/states/BillingContract.kt).

`BillingContract` governs the evolution of both `Billing` and `BillingChip` states. The following commands are supported:
* `Issue` - to issue a `BillingState`.
* `Return` - to return a `BillingState` in the end of the billing period.
* `Revoke` - to revoke a `BillingState` as a result of a governance action.
* `Close` - to close a `BillingState` when all obligations are settled.
* `ChipOff` - to chip off `BillingChipState` from a `BillingState`.
* `AttachBack` - to attach back *unspent* `BillingChipStates` to their `BillingState`.
* `UseChip` - to use `BillingChipStates` inside a business transaction.
 
A `BillingState` can exist in one of the following statuses:
* `ACTIVE` - the states that can be used to pay for transactions
* `RETURNED` - the states that have been returned to the BNO in the end of the billing period.
* `REVOKED` - the states that have been revoked by the BNO as a result of a governance action.
* `CLOSED` - the states that have been fully settled. 

## Issuing a BillingState

A `BillingState` can be issued via `IssueBillingStateFlow`. The flow should be called by the BNO.

```kotlin
class IssueBillingStateFlow(
    private val owner : Party, // a party to issue the BillingState to
    private val amount : Long, // the maximum amount of the Billing Chips that can be chipped off from this Billing State. Can be 0 for an unbounded spending.
    private val expiryDate : Instant? = null, // the Billing State's expiry date. All transactions that include Billing States with an expiry date defined must also contain a Time Window.
    private val externalId : String? = null // a billing externalId to differentiate between multiple active billing states.  
)
```

## Chipping Off

Actual business transactions should never contain `BillingStates` as inputs to prevent the private transaction history from being leaked.  

`BillingChipStates` can be chipped off from `BillingStates` via `ChipOffBillingStateFlow`. The flow has to be invoked by a member who owns the `BillingState`. Chipping off doesn't require the BNO's signature.  

```kotlin
class ChipOffBillingStateFlow(private val billingState : StateAndRef<BillingState>, // a reference to the Billing State to chip off from
                              private val chipAmount : Long, // an amount of each individual Billing Chip. ChipOffBillingStateFlow can chip off multiple BillingStateChips of the same amount in one go. 
                              private val numberOfChips : Int = 1, // a number of chips to chip off from the billingState. The total chipping-off amount is equal to numberOfChips * chipAmount.
                              private val timeTolerance : Duration = TIME_TOLERANCE // a time tolerance for the transaction Time window. Used only if the [billingState] defines an expiry date.
                              ) 
```

## Using Chips

`MemberBillingDatabaseService` provides a number of convenience methods for fetching  `BillingChipStates` from the vault:

```kotlin
class MemberDatabaseService {
    fun getBillingStateByLinearId(linearId : UniqueIdentifier) : StateAndRef<BillingState>? 
    fun getOurActiveBillingStates() : List<StateAndRef<BillingState>> 
    fun getOurActiveBillingStatesForExternalId(externalId: String) : List<StateAndRef<BillingState>>
    fun getOurActiveBillingStatesByIssuer(issuer : Party) : List<StateAndRef<BillingState>> 
    fun getBillingChipStatesByBillingStateLinearId(billingStateLinearId : UniqueIdentifier) : List<StateAndRef<BillingChipState>> 
    fun getBillingChipStateByLinearId(chipLinearId : UniqueIdentifier) : StateAndRef<BillingChipState>? 
    fun getOurBillingChipStatesByIssuer(issuer : Party) : List<StateAndRef<BillingChipState>> 
}
```

To start using billing / metering you would need to:
1. Add a logic to your contract code, that verifies that each of the transaction participants has included enough of the Billing Chips as transaction inputs.
2. When building a transaction in your flows, add `BillingChipStates` as transaction inputs with the respective `BillingStates` as reference inputs.
3. Add a `UseChip` command for each of the billable participants.
4. Add a transaction time window if any of the Billing States defines an expiry date. The lower boundary of the time window must be greater or equal to the latest of the expiry dates.  

## Returning a BillingState

In the end of the billing period, the BNO can request a member to return their `BillingState` via `RequestReturnOfBillingStateFlow`:

```kotlin
class RequestReturnOfBillingStateFlow(
    private val billingState : StateAndRef<BillingState> // the Billing State to return
    )

```

To request a member to return all of their `BillingStates` (in the cases when there can be multiple of those) use `RequestReturnOfBillingStateForPartyFlow`:

```kotlin
class RequestReturnOfBillingStateForPartyFlow(
    private val party : Party // the party that is required to return their BillingStates
    )
```

When returning the states, each member would automatically attach all of the *unused* `BillingChipStates` back to their respective `BillingStates` to decrement the spent amount.

## Revoking a BillingState

BNOs can use `RevokeBillingStateFlow` to revoke a `BillingState` as a result of a governance action. Revocation doesn't require the member's signature.

```kotlin
class RevokeBillingStateFlow(
    private val billingState : StateAndRef<BillingState> // the BillingState to revoke
    )
```

To revoke all `BillingStates` for a party (in the cases when there can be multiple of those) use `RevokeBillingStatesForPartyFlow`.

```kotlin
class RevokeBillingStatesForPartyFlow(
    val party : Party // the party to revoke the states from
    )
```

## Closing a BillingState

After a `BillingState`'s obligations are settled, the `BillingState` can be closed via `CloseBillingStateFlow`.

```kotlin
class CloseBillingStateFlow(
    private val billingState : StateAndRef<BillingState> // the BillingState to close
    )
```

## How to add Billing Service to your project

Add the following lines to the `repositories` and `dependencies` blocks of your `build.gradle` file:

```
    repositories {
        maven {
          url 'https://ci-artifactory.corda.r3cev.com/artifactory/corda-solutions-releases'
        }
    }


    dependencies {
        cordapp "com.r3.businessnetworks:billing-app:2.0"
        cordapp "com.r3.businessnetworks:billing-contracts-and-states:2.0"
    }
```

# Deploying the Billing Service


## Getting the Jars

You will need 2 jars to run the Billing Service

- billing-app:X.0.jar
- billing-contracts-and-states:X.0.jar

If the version is 2.0, then you will also need (this is bundled for 3.0 onwards): 

-  businessnetworks-utilities-2.0.jar

You can get the jars from the r3 artifactory: 

https://ci-artifactory.corda.r3cev.com/artifactory/webapp/#/artifacts/browse/tree/General/corda-solutions-releases


Alternatively, you can build the project from source, from corda-solutions directory run: 
 ```./gradlew build``` 


## Install the Jars on the node


1. Place the billing-app:X.0.jar and billing-contracts-and-states:X.0.jar into your node's cordapp directory.

2. Create/ move into the cordapps/config directory.

3. Create a billing-app.conf file with the following content, updated for your specific network details: 

    ```
    notaryName = "O=Notary, L=London, C=GB"
    bnoName = "O=BNO, L=London, C=GB"
    ```

4. Restart the node.

