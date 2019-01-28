![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

# Business Network Membership Service (BNMS)

*Contents of this article assume the reader's familiarity with the concepts of *Business Networks*. Please see [this page](https://solutions.corda.net/business-networks/intro.html) for more information.*

Business Network Membership Service aims to solve the following problems:
* On-boarding of new members to a Business Network. 
* Suspension of members from a Business Network.
* Distribution of a membership list to the Business Network members.
* Association of a custom metadata with a node's identity.
* Participation in multiple Business Networks by a single node.

BNMS provides the following API extension points:
* Custom Membership Metadata. Nodes can associate a custom metadata with their membership states. Membership Metadata might contain such fields as *role, address, displayed name, email* and other used-defined fields. Membership Metadata can also be represented with different classes for different business networks. Associated metadata is distributed to Business Network members as a part of the *general membership distribution mechanism*.  
* Automatic acceptance of memberships requests. BNOs can implement a custom verification code that would be run against every incoming membership request to determine whether it's eligible for auto-activation. Auto-activated members can start transacting on the Business Network straight-away without a need for a separate membership activation step. Please see [Membership Auto Approvals](#membership-auto-approval) section.
* Custom membership metadata verification. Custom verification can be added by [overriding](https://docs.corda.net/head/flow-overriding.html) `RequestMembershipFlowResponder` and `AmendMembershipMetadataFlowResponder` flows (please see further sections). Please see [Custom Membership Metadata Verification](#custom-membership-metadata-verification) section.   

Please see the [design doc](./design/design.md) for more information about the technical design considerations.

Please see [FullBNMSFlowDemo](./membership-service/src/test/kotlin/com/r3/businessnetworks/membership/flows/FullBNMSFlowDemo.kt) for a detailed how-to-use example.

## How to add BNMS to your project

Add the following lines to the `repositories` and `dependencies` blocks of your `build.gradle` file:

```
    repositories {
        maven {
          url 'http://ci-artifactory.corda.r3cev.com/artifactory/corda-solutions-dev'
        }
    }


    dependencies {
        cordapp "com.r3.businessnetworks:membership-service:2.0-SNAPSHOT"
        cordapp "com.r3.businessnetworks:membership-service-contracts-and-states:2.0-SNAPSHOT"
    }
```

## Structure

BNMS contains implementations of flows, states and contracts to model memberships on a Business Network. It consists of 2 CorDapps:
* `membership-service-contracts-and-states` - contracts and states
* `membership-service` - flows for both BNO and member CorDapps 

*Both CorDapps have to be installed on the nodes of BNOs and members.*

### States

Memberships are represented with a [MembershipState](./membership-service-contracts-and-states/src/main/kotlin/com/r3/businessnetworks/membership/states/Membership.kt). Users can associate a custom metadata with their `MembershipState` via `membershipMetadata` field. `MembershipState` is generic and doesn't enforce any restrictions over the type of the metadata.

`MembershipState` can exist in the following statuses: 
* `PENDING` - the very first status for all newly issued memberships. To be able to transact on the Business Network `PENDING` memberships need to be activated first.
* `ACTIVE` - active membership holders can transact on the Business Network.
* `SUSPENDED` - Business Network members can be temporarily suspended by their BNO, for example as a result of a governance action. Suspended members can't transact on the Business Network.

### Membership contract

`MembershipState` evolution is curated by [MembershipContract](./membership-service-contracts-and-states/src/main/kotlin/com/r3/businessnetworks/membership/states/Membership.kt). `MembershipContract` verifies the evolution of `MembershipState`s only without verifying Membership Metadata, as it's a generic parameter.   

Membership Metadata evolution can be verified by [overriding responder flows](https://docs.corda.net/head/flow-overriding.html) at the BNO side. Please see [Custom Membership Metadata Verification](#custom-membership-metadata-verification) section for more information.  

### Flows

BNMS flows are split into two packages: `bno` and `member` (with the flows for BNOs and members respectively).

Flows that can be invoked by members: 
* `RequestMembershipFlow` - to request a membership. 
* `AmendMembershipMetadataFlow` - to update a membership metadata
* `GetMembershipsFlow` - to pull down a memberships list from a BNO. Members retrieve the full list on the first invocation only. All subsequent updates are delivered via push notifications from the BNO. Memberships cache can be force-refreshed by setting `forceRefresh` of `GetMembershipsFlow` to true. Members that are missing from the Network Map are filtered out from the result list.

Flows that can be invoked by BNO: 
* `ActivateMembershipFlow` - to activate a `PENDING` membership.
* `SuspendMembershipFlow` - to suspend an `ACTIVE` membership.

Activation and suspension transactions don't require the member's signature. BNO is eligible to suspend memberships unilaterally, for example as a result of a governance action.  

## Multiple Business Networks

BNMS provides a support for multiple business networks. Business Networks are uniquely identified by BNO's `Party` object. All flows that assume any member -> BNO interactions require BNO's identity as a mandatory parameter.   

## Configuration 

CorDapp configuration is red from `cordapps/config/membership-service.conf` file with a fallback to `membership-service.conf` on the CorDapp's classpath.

### Member configuration

```hocon
// whitelist of accpted BNOs. Attempt to communicate to not whitelisted BNO would result into an exception
bnoWhitelist = ["O=BNO,L=New York,C=US", "O=BNO,L=London,C=GB"]
``` 

### BNO configuration
```hocon
// Name of the Notary
notaryName = "O=Notary,L=Longon,C=GB"
```
## Designing your flows for Business Networks

As Business Networks is an *application level* concept, memberships *have to* be verified manually inside the flows of your CorDapp. **Corda does not perform any membership checks at the platform level**.

An example of logic for a counterparty's membership verification can be found in [BusinessNetworkAwareInitiatedFlow](./membership-service/src/main/kotlin/com/r3/businessnetworks/membership/flows/member/support/BusinessNetworkAwareInitiatedFlow.kt):

```kotlin
/**
 * Extend from this class if you are a business network member and you don't want to be checking yourself whether
 * the initiating party is also a member. Your code (inside onCounterpartyMembershipVerified) will be called only after
 * that check is performed. If the initiating party is not a member an exception is thrown.
 */
abstract class BusinessNetworkAwareInitiatedFlow<out T>(protected val flowSession : FlowSession) : FlowLogic<T>() {
    @Suspendable
    override fun call(): T {
        verifyMembership(flowSession.counterparty)
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
        // Memberships list contains valid active memberships only. So we need to just make sure that the membership exists.
        subFlow(GetMembershipsFlow(bnoIdentity()))[initiator] ?: throw NotAMemberException(initiator)
    }
}
```

The easiest way of making your flow *"business network aware"* is to extend from `BusinessNetworkAwareInitiatedFlow`. 

> Please note that during development you can take an advantage of extending from `BusinessNetworkOperatorFlowLogic` and `BusinessNetworkOperatorInitiatedFlow` if you are developing custom flows for BNO.

## Custom Membership Metadata Verification

To implement custom verification of Membership Metadata: 
1. Override `RequestMembershipFlowResponder` and `AmendMembershipMetadataFlowResponder` flows.
2. Add your custom verification logic to the `verifyTransaction` method.

For example:
```kotlin
@InitiatedBy(RequestMembershipFlow::class)
class RequestMembershipFlowResponderWithMetadataVerification(session : FlowSession) : RequestMembershipFlowResponder(session) {
    @Suspendable
    override fun verifyTransaction(builder : TransactionBuilder) {
        super.verifyTransaction(builder)
        val membership = builder.outputStates().filter { it.data is MembershipState<*> }.single().data as MembershipState<MyMembershipMetadata>
        if (membership.membershipMetadata.role != "BORROWER") {
            throw FlowException("Invalid role ${membership.membershipMetadata.role}")
        }
    }
}
```

## Membership Auto-Approval

To implement automatic membership approvals:
1. Override `RequestMembershipFlowResponder` flow.
2. Add your custom verification logic to the `activateRightAway` method.

For example:
```kotlin
@InitiatedBy(RequestMembershipFlow::class)
class RequestMembershipFlowResponderWithAutoApproval(session : FlowSession) : RequestMembershipFlowResponder(session) {

    @Suspendable
    override fun activateRightAway(membershipState : MembershipState<Any>, configuration : BNOConfigurationService) : Boolean {
        // Add your custom request verification logic here.
        // You can call an external system, verify the request against a pre-configured whitelist and etc.
        // For example to approve *any* incoming membership request - just return *true* from this method.
        return true
    }
}
``` 