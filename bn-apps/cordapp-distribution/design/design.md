![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

# CorDapp distribution service

DOCUMENT MANAGEMENT
---

## Document Control

| Title                | Ledger Synchronisation Service                            |
| -------------------- | ------------------------------------------------------------ |
| Date                 | 18 June 2018                                                |
| Author               | Ivan Schasny |
| Distribution         | Design Review Board, Product Management, Solutions Engineering, Platform Delivery |
| Corda target version | OS                                                   |

## HIGH LEVEL DESIGN

### Glossary

* *SE* - Solutions Engineer
* *BN* - Business Network
* *BNO* - Business Network Operator. A well-known party on the network.
* *CDS* - CorDapp distribution service
* *CZ* - Compatibility Zone

### Overview

This proposal describes the architecture of a reference implementation for the CorDapp distribution service.

### Background

A problem of distributing CorDapp updates is actual for every Business Network running on Corda. An automated software distribution mechanism would allow Business Network Operator to [TODO]

### Scope

Design a reference implementation of CorDapp, that would allow Business Network Operator to distribute Corda and CorDapp updates to the members of their Business Network.

In-scope:
* Distribution of initial software for a new participant to join the Business Network
* Distribution of CorDapps by BNO to their Business Network members
* Distribution of Corda updates by BNO to their Business Network members
* Banning contract-and-states jars from transacting on the network

Out of scope:
* CZ on-boarding. This design document assumes that a user already has a valid CZ certificate.
* Initial node setup.
* Update installation. To install an update, a node needs to be switched into the flow draining mode and then restarted. Both of the operations would require a human intervention and can't be automated at the moment.

### Timeline

* Projects, which go live on Corda in 2018 are asking to have a reference implementation available *asap*.
* This solution will evolve over time. We need to get more feedback about usage patterns which might result in future design changes.  

### Requirements

* New participant should be able to join a BN in an automated way
* BNO should be able to distribute CorDapp and platform updates to the BN members
* BNO should be able to distribute a list of allowed / deprecated CorDapp and platform versions
* BNO should be able to promptly notify BN members about a CorDapp / platform version deprecation

### Assumptions

* The user has a valid CZ certificate
* The user has been on-boarded to the Business Network via the BNMS [LINK] service

### Target solution

The proposal is to implement CorDapp distribution service at the CorDapp level, for the following reasons:

[ADD SOME REASONS]

#### CorDapps list

On start, each node should pull down a list of CorDapps which **must** be installed for it to be able to transact on the BN. This list will be distributed by BNO via Corda flows. BNO will be notifying each member about any update to it. The proposed data structure:

```
{
  "lastUpdated" : "2018-06-20T13:59:58+00:00",
  "cordapps" : [
    {
      "vendor" : "accenture",
      "name" : "accenture-states",
      "currentVersion" : "1.2",
      "minimumVersion" : "0.9",
      "hash" : "aldsdjfsjdfjksdf",
      "vendorCertificate" : "blahblahblah",
      "distribution" : { // specifies the way, how the CorDapp is distributed.
        "type" : "flows" // for example this application can be downloaded via bundled Corda Flows
      }
    },
    {
      "vendor" : "accenture",
      "name" : "accenture-flows",
      "currentVersion" : "1.3",
      "minimumVersion" : "1.0",
      "hash" : "jfbasvdbascdbvac",
      "vendorCertificate" : "blahblahblah"
      "distribution" : {
        "type" : "web", // this CorDapp can be downloaded via HTTP
        "url" : "http://my-super-cdn"
      }
    },
    ...
  ]  
}
```

Each CorDapp definition can be associated with a *distribution mechanism*. *Distribution mechanism* defines how a CorDapp **can** be downloaded, however it wouldn't enforce a node to perform any particular **action**. *Node administrators* will be able to specify what to do in a response to a new update or a minimum version bump. For example this could be such things as to send an email or to raise a warning on the monitoring system or to download a CorDapp into a local folder and etc. This configuration will be done on a *node level* (described in the further sections), as it might vary from a node to node even within a single BN. It will be up to *node administrators* to configure their CDS CorDapps to find the right balance between automation and their internal security policies. More about automation is described in the further sections.

Cordapp descriptor also contains some generic information about a CorDapp, such as `vendor`, `name`, `currentVersion`, `minimumVersion`, the `hash` of the jar file as well as the `vendorCertificate`, which the CorDapp jar can be validated with. `MinimumVersion` defines the minimum supported version of the CorDapp on this Business Network. `MinimumVersion` can be bumped by the BNO if a critical vulnerability has been discovered or if a new version of the CorDapp is not backward compatible.

Given a nature of the DLT applications, deploying an update to the whole network in the same time might not be feasible, unless the whole of the network can be shut down to perform an update. Shutting the whole of the network down is possible, but highly unlikely, as a single node might be involved into multiple Business Networks, with unrelated governance structures. BNO will not able to enforce a node to install updates. Its a responsibility of the node owner to install updates promptly if their CorDapps are outdated and especially if a CorDapp's version is below the allowed minimum. More about automation is described in the further sections.

CorDapps should be designed to be backward compatible. Corda provides mechanisms for flow versioning [LINK] and contract constraints [LINK] at the platform level out-of-the-box.

CorDapp descriptors will be persisted into the database and will shared between instances in HA mode.

#### Distribution mechanisms

*Distribution mechanisms* will be pluggable and extensible. Each CorDapp can be associated with a separate *distribution mechanism*, such as `CordaFlows`, `HTTP` or `FTP` and etc. BNOs will be able to define own *distribution mechanisms* if they are missing from the standard implementation. *Distribution mechanism* must have a name and might also have other optional configuration parameters, such as download url and etc.

On a node's side, the *node administrator* will be able to associate each *distribution mechanism* with a *download adaptor*. All *download adaptors* will need to implement a standard interface (provided with the CDS). The *download adaptors* will be responsible for downloading a CorDapp based on the provided configuration. The configuration will consist of the CorDapp descriptor (described in the previous section) and the local configuration, which will have to be provided by a *node administrator*. The proposed configuration structure:
```
{
  ...
  "distribution" : {

    "web" : { // defines a download adaptor for the "web" distribution mechanism
      "downloadAdaptor" : "com.my.adapters.MyCdnDownloadAdapter", // this adaptor will be used to download updates of CorDapps with the "web" distribution mechanism
      "configuration" : { // this is the custom configuration, which will be passed to the MyCdnDownloadAdapter along with descriptor of a CorDapp.
        "authentication" : { }, // some custom auth parameters
        "downloadFolder" : "/home/corda/updates/",
        ...
      },
    },
    ...
  }
  ...
}
```
If some *distribution mechanism* is not associated with any *download adaptor* - then no CorDapp updates with this *distribution mechanism* will be downloaded. Node administrators might choose to not to configure any *download adaptors* if they would like to perform download manually.

Each jar's signature will be verified by a *download adaptor* by default.

#### Notifications

*Node administrators* will be able to configure custom notification for events of the following types:
* `onUpdate`. The notification will be triggered when a new update of a CorDapp is available.
* `onDeprecation`. The notification will be triggered when an installed version of a CorDapp is below the `minimumVersion` from the CorDapp descriptor.

Notification will be send via *notification adaptors*. *Notification adaptors* will be defined by a standard interface and can be implemented by users. Some *notification adaptors* will be provided with a reference implementation.  

*Notification adaptors* are responsible for sending a notification based on a descriptor of an updated / deprecated CorDapp and a local configuration, provided by the *node administrator*. *Notification adaptor* might be responsible for such things as sending an email or dropping a message to MQ and etc. Developers will be able to integrate the Notification adaptors with their internal monitoring / alerting systems.

Proposed configuration:
```
{
  ...
  "notification" : {
    "onUpdate" : {
      "notificationAdapter" : "com.my.adapters.SendEmailViaSMTPAdapter",
      "configuration" : {
        // some custom configuration
      }
    },
    "onDeprecate" : {
      "notificationAdapter" : "com.my.adapters.RaiseIssueOnMonitoringDashboardAdapter",
      "configuration" : {
        // some custom configuration
      }
    }
  },
  ...
}
```

#### CorDapp structure

All CorDapps should contain a standard CorDapp descriptor inside their `META-INF` folder.

CorDapps have to be signed via standard java signing mechanism if their signature needs to be verified.

#### CorDapps list processing logic

![CorDapps list processing logic](./resources/cordapps_list_processing.png).

### API extension points

* Custom notification adaptors which can be integrated into internal systems
* Custom download adaptors
* Custom distribution mechanisms
