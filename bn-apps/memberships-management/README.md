![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

# Business Network Membership Service

This is a reference implementation for the Business Network Membership Service. The implementation can, but doesn't have to be used as-is. Business Networks are encouraged to teak the code to fit their requirements.

Please see the [design doc](./design/design.md) for more information about the problem this service is aimed to tackle. 

BNMS consists of 2 CorDapps:
* `membership-service-contracts-and-states` - contracts and states
* `membership-service` - flows for both BNO and members

Both of the CorDapps are required to be installed to the nodes of all members as well as BNO's node.

CorDapp configuration is red from the `membership-service.properties` file on the CorDapp's classpath.

Please see [FullBNMSFlowDemo](./membership-service/src/test/kotlin/net/corda/businessnetworks/membership/FullBNMSFlowDemo.kt) for how-to-use example.

## Member configuration 

| Property        | Description         |
| ------------- |:-------------:|
| `net.corda.businessnetworks.membership.bnoName` | Fully qualified X500 name of the BNO |

## BNO configuration 

| Property        | Description         |
| ------------- |:-------------:|
| `net.corda.businessnetworks.membership.notaryName` | Fully qualified X500 name of the Notary to be used for membership states notarisation|
| `net.corda.businessnetworks.membership.cacheRefreshPeriod` | Specifies how often (in seconds) BN members should be refreshing their membership list caches. If this attribute is not set, then the BN members will pull membership list only once, when their node starts, and then will rely on the BNO to notify them about all memberships changes.|
| `net.corda.businessnetworks.membership.notificationsEnabled` | If `true`, then the BNO will notify all members about any change to the memberships |