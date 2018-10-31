![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

# Business Network Membership Service (BNMS)

Contents of this article assume reader's familiarity with the concepts of Business Network and Business Network Operator. More information can be found [here](https://solutions.corda.net/business-networks/intro.html).

Business Network Membership Service aims to solve the following problems:
* On-boarding of new members to a business network. 
* Suspension of members from a business network.
* Distribution of a membership list to the business network members.
* Association of a custom metadata with a node's identity.
* Participation in multiple business networks for a single node.

BNMS supports the following API extension points:
* Custom metadata. Nodes can associate a custom metadata with their Corda identity. The metadata might contain such fields as role, address, displayed name, email and etc. The metadata can be different for different business networks. It can be even represented with different classes.
* Custom membership contract implementations. BNOs can extend `MembershipContract` with their own verification logic (for example to verify custom metadata evolution).  
* Automatic acceptance of memberships requests. BNOs can implement custom verification code that would be run against incoming membership requests to determine whether they are eligible for auto-activation. Auto-activated members will be able to start transacting straight-away.

Please see the [design doc](./design/design.md) for more information about technical design considerations.

## Structure

BNMS includes implementations of flows, states and contracts to model memberships on a Business Network.

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

Membership metadata evoltion can be verified in the following ways:
* In the responding flows, by overriding them at the BNO's side (_off-ledger verification_). Will be introduced in Corda 4.
* By extending the functionality provided by the `MembershipContract` (_on-ledger verification_). `MembershipContract` is an `open` class and can be extended by users to add any custom verification logic. A custom contract implementation can be provided to the BNMS via `membershipContractName` configuration property of the BNO's CorDapp.

### Flows

BNMS flows are split in 2 packages `bno` and `member` - with the flows for BNOs and members respectively.

Flows that can be invoked by members: 
* `RequestMembershipFlow` - to request a membership. 
* `AmendMembershipMetadataFlow` - to update a membership metadata
* `GetMembershipsFlow` - to pull memberships list from the BNO. Members retrieve a full memberships list from the BNO on the first invocation of `GetMembershipsFlow` and then cache it in memory. All subsequent updates to the memberships are delivered via push. Memberships cache can be force-refreshed by setting `forceRefresh` of `GetMembershipsFlow` to true. Members that are missing from the Network Map are filtered out from the result list.

Flows that can be invoked by BNO: 
* `ActivateMembershipFlow` - to activate a `PENDING` membership
* `SuspendMembershipFlow` - to suspend an `ACTIVE` membership

Please see [FullBNMSFlowDemo](./membership-service/src/test/kotlin/net/corda/businessnetworks/membership/FullBNMSFlowDemo.kt) for a detailed how-to-use example.

Note that during development you can take advantage of the following classes that encapsulate some commonly used logic:
* If you are a business network operator: `BusinessNetworkOperatorFlowLogic` and `BusinessNetworkOperatorInitiatedFlow`
* If you are a business network member: `BusinessNetworkAwareInitiatedFlow`

Look at their respective class annotations for details.

If you don't want to manually activate every membership request and would like to instead automate the process then implement the interface `MembershipAutoAcceptor` and add a respective entry to the BNO's configuration file.

## Configuration 

CorDapp configuration is red from `cordapps/config/membership-service.conf` file with a fallback to `membership-service.conf` on the CorDapp's classpath.

### Member configuration

```hocon
// X500 name of the BNO
bnoName = "O=BNO,L=New York,C=US"
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