![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

# Business Network Membership Service

This is a reference implementation for the Business Network Membership Service. The implementation can, but doesn't have to be used as-is. Business Networks are encouraged to tweak the code to fit their requirements.

Please see the [design doc](./design/design.md) for more information about the problem this service is aimed to tackle. 

BNMS consists of 2 CorDapps:
* `membership-service-contracts-and-states` - contracts and states
* `membership-service` - flows for both BNO and members

Both of the CorDapps are required to be installed to the nodes of all Business Network participants as well as to the BNO's node.

CorDapp configuration is red from `cordapps/config/membership-service.conf` file with a fallback to `membership-service.conf` on the CorDapp's classpath.

Please see [FullBNMSFlowDemo](./membership-service/src/test/kotlin/net/corda/businessnetworks/membership/FullBNMSFlowDemo.kt) for how-to-use example.

Note that during development you can take advantage of the following classes that encapsulate some commonly used logic:
* If you are a business network operator: `BusinessNetworkOperatorFlowLogic` and `BusinessNetworkOperatorInitiatedFlow`
* If you are a business network member: `BusinessNetworkAwareInitiatedFlow`

Look at their respective class annotations for details.

> By default membership states for parties that don't exist in the Network Map are not considered as valid. To change this behaviour please see `GetMembershipsFlow.filterOutNotExisting` flag. 

## Member configuration 

| Property        | Description         |
| ------------- |:-------------:|
| `bnoName` | Fully qualified X500 name of the BNO |

## BNO configuration 

| Property        | Description         |
| ------------- |:-------------:|
| `notaryName` | Fully qualified X500 name of the Notary to be used for membership states notarisation|
| `cacheRefreshPeriod` | Specifies how often (in milliseconds) BN members should be refreshing their membership list caches. If this attribute is not set, then the BN members will pull membership list only once, when their node starts, and then will rely on the BNO to notify them about all memberships changes.|
| `notificationsEnabled` | If `true`, then the BNO will notify all members about any change to the memberships |

## Behaviour customization

If you don't want to manually activate every membership request and would like to instead automate the process then implement the interface `MembershipAutoAcceptor`. Then add a new entry into the membership-service.properties file

| Property        | Description         |
| ------------- |:-------------:|
| `membershipAutoAcceptor` | Class implementing the MembershipAutoAcceptor interface, including package name |