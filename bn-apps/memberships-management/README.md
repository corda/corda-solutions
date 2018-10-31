![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

# Business Network Membership Service (BNMS)

Contents of this article assume reader's familiarity with the concepts of Business Network and Business Network Operator. Please see [this page](https://solutions.corda.net/business-networks/intro.html) for more information about Business Networks.

Business Network Membership Service aims to solve the following problems:
* On-boarding of new members to a business network. 
* Suspension of members from a business network.
* Distribution of a membership list to the business network members.
* Association of a custom metadata with a node's identity.
* Participation in multiple business networks by a single node.

BNMS provides the following API extension points:
* Custom membership metadata. Nodes can associate a custom metadata with their memberships. The metadata might contain such fields as role, address, displayed name, email and etc. The metadata can be different for different business networks. It can be even represented with different classes. Associated metadata gets distributed to other Business Network members as a part of general membership distribution mechanism. 
* Custom *Membership Contract* implementations. BNOs can extend from `MembershipContract` and add their custom verification logic (for example to verify their custom metadata evolution).  
* Automatic acceptance of memberships requests. BNOs can implement a custom verification code that would be run against the incoming membership requests to determine whether they are eligible for auto-activation. Auto-activated members will be able to start transacting straight-away (otherwise a separate Activation step is required).

Please see the [design doc](./design/design.md) for more information about technical design considerations.

Please see [FullBNMSFlowDemo](./membership-service/src/test/kotlin/net/corda/businessnetworks/membership/FullBNMSFlowDemo.kt) for a detailed how-to-use example.

## Structure

BNMS provides implementations of flows, states and contracts to model memberships on a Business Network.

BNMS consists of 2 CorDapps:
* `membership-service-contracts-and-states` - contracts and states
* `membership-service` - flows for both BNO and member CorDapps 

Both of the CorDapps are required to be installed to the nodes of all Business Network participants as well as to the BNO's node.

### States

Memberships are represented with a `MembershipState`. Users can associate a custom metadata with their `MembershipState` via `membershipMetadata` field. `MembershipState` is generic and doesn't enforce any restrictions over the type of the metadata.

`MembershipState` can exist in the following statuses: 
* `PENDING` - the very first status of each membership. To be able to transact on the Business Network `PENDING` memberships need to be activated first.
* `ACTIVE` - active membership holders can transact on the Business Network.
* `SUSPENDED` - Business Network members can be temporarily suspended by their BNO as a result of a governance action for example. Suspended members can't transact on the Business Network.

### Membership contract

`MembershipState` evolution is curated by `MembershipContract`. However, `MembershipContract` can't verify evolution of the membership metadata as it's a generic parameter.   

Membership metadata evolution can be verified in the following ways:
* In the responding flows, by overriding them at the BNO's side (_off-ledger verification_). Will be introduced in Corda 4.
* By extending the `MembershipContract` (_on-ledger verification_). `MembershipContract` is an `open` class and can be extended by users to add a custom verification logic. A custom contract implementation can be provided to the BNMS via `membershipContractName` configuration property supported by both BNO and member CorDapps.

### Flows

BNMS flows are split into 2 packages: `bno` and `member` (with the flows for BNOs and members respectively).

Flows that can be invoked by members: 
* `RequestMembershipFlow` - to request a membership. 
* `AmendMembershipMetadataFlow` - to update a membership metadata
* `GetMembershipsFlow` - to pull down a memberships list from a BNO. Members retrieve the full list on the first invocation only. All subsequent updates are delivered via push notifications from the BNO. Memberships cache can be force-refreshed by setting `forceRefresh` of `GetMembershipsFlow` to true. Members that are missing from the Network Map are filtered out from the result list.

Flows that can be invoked by BNO: 
* `ActivateMembershipFlow` - to activate a `PENDING` membership.
* `SuspendMembershipFlow` - to suspend an `ACTIVE` membership.

Activation and suspension transactions don't require the member's signature. BNO is eligible to suspend memberships unilaterally, for example as a result of a governance action.  

### Multiple Business Networks

BNMS provides a support for multiple business networks. Business Networks are uniquely identified by BNO's `Party` object. All flows that assume any member -> BNO interactions take BNO's identity as a mandatory parameter.   

### Membership Auto Approval

If you don't want to manually activate every membership request and would like to instead automate the process then implement the interface `MembershipAutoAcceptor` and add a respective entry to the BNO's configuration file.

## Configuration 

CorDapp configuration is red from `cordapps/config/membership-service.conf` file with a fallback to `membership-service.conf` on the CorDapp's classpath.

### Member configuration

```hocon
// whitelist of accpted BNOs. Attempt to communicate to not whitelisted BNO would result into an exception
bnoWhitelist = ["O=BNO,L=New York,C=US", "O=BNO,L=London,C=GB"]

// Name of the contract to validate membership transactions with. 
// Defaults to "net.corda.businessnetworks.membership.states.MembershipContract" if not specified
membershipContractName = "com.app.MyMembershipContract"

``` 

### BNO configuration
```hocon
// Name of the Notary
notaryName = "O=Notary,L=Longon,C=GB"

// Name of the contract to validate membership transactions with. 
// Defaults to "net.corda.businessnetworks.membership.states.MembershipContract" if not specified
membershipContractName = "com.app.MyMembershipContract"

// Name of the class that implements MembershipAutoAcceptor interface. Optional parameter.
membershipAutoAcceptor = "com.app.MyMembershipAutoAcceptor"

```
### Designing your flows for business networks

As Business Networks is an *application level* concept, memberships have to be verified manually inside the flows of your CorDapp. *Corda does not perform any membership checks by itself*.

Logic for a counterparty's membership verification can be found in `BusinessNetworkAwareInitiatedFlow`:

```kotlin
/**
 * Extend from this class if you are a business network member and you don't want to be checking yourself whether
 * the initiating party is also a member. Your code (inside onCounterpartyMembershipVerified) will be called only after
 * that check is performed. If the initiating party is not a member an exception is thrown.
 */
abstract class BusinessNetworkAwareInitiatedFlow<out T>(protected val session: FlowSession) : FlowLogic<T>() {
    @Suspendable
    override fun call(): T {
        verifyMembership(session.counterparty)
        return onOtherPartyMembershipVerified()
    }

    /**
     * Will be called once counterpart's membership is successfully verified
     */
    @Suspendable
    abstract fun onOtherPartyMembershipVerified() : T

    /**
     * Identity of the BNO to verify counterpart's membership against
     */
    abstract fun bnoIdentity() : Party

    @Suspendable
    private fun verifyMembership(initiator : Party) {
        val membership = subFlow(GetMembershipsFlow(bnoIdentity()))[initiator]
        val configuration = serviceHub.cordaService(MemberConfigurationService::class.java)
        // 1. membership should exist
        // 2. membership have to be active
        // 3. membership have to be validated by the correct contract
        if(membership == null || !membership.state.data.isActive() || membership.state.contract != configuration.membershipContractName()) {
            throw NotAMemberException(initiator)
        }
    }
}

```

When verifying a membership it's important to make sure that:
* The membership actually exist
* The membership is active
* The membership is verified by the expected contract

The easiest way of making your flow *"business network aware"* is to extend from `BusinessNetworkAwareInitiatedFlow`. Otherwise a counterparty's membership verification would have to be performed manually.

> It's important to keep in mind that applications which are designed for Business Networks should be taking the list of members from their membership service instead of the Network Map (for example to populate a ComboBox in the UI). 

> Please note that during development you can take advantage of extending from `BusinessNetworkOperatorFlowLogic` and `BusinessNetworkOperatorInitiatedFlow` if you are developing custom flows for BNO.

 