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
* *CDS* - CorDapp Distribution Service
* *CZ* - Compatibility Zone

### Overview

This proposal describes the architecture of a reference implementation for the CorDapp Distribution Service.

### Background

Decentralised platforms have introduced some operational challenges, one of which is a coordinated distribution and installation of software updates. Deploying an update to the whole BN simultaneously might be not feasible, unless all the nodes in this BN can be shut down in the same time to perform an upgrade. Such *maintenance windows* might not work in the cases when a single node is involved into multiple BNs, with unrelated governance structures. This design introduces a concept of CorDapp Distribution Service which aims to tackle these issues.

### Scope

Design a reference implementation of CorDapp, that would allow Business Network Operator to distribute CorDapp updates to the members of their Business Network.

In-scope:
* Distribution of CorDapp updates to the Business Network
* Revocation of CorDapps from the Business Network

Out of scope:
* CZ on-boarding. This design document assumes that a user already has a valid CZ certificate.
* Installation of updates. To install an update, a node needs to be switched into the *flows draining mode* and then restarted. Both of the operations would require a human intervention and can't be automated at the moment.
* Distribution of the platform updates. It's a responsibility of *node administrators* to make sure that their version of Corda is above the `minimumPlatformVersion` defined in the [Network Parameters](https://docs.corda.net/network-map.html#network-parameters).

### Timeline

* Projects, which go live on Corda in 2018 are asking to have a reference implementation available *asap*.
* This solution will evolve over time. We need to get more feedback about usage patterns which might result in future design changes.  

### Requirements

* Business Networks should have a mechanism to distribute a CorDapp and its updates to the BN members
* Business Networks should have a mechanism to deprecate a CorDapp from the BN

### Assumptions

* The user has a valid CZ certificate
* The user has been on-boarded to the Business Network via the [Business Network Membership Service](https://github.com/corda/corda-solutions/blob/master/bn-apps/memberships-management/design/design.md)

### Target solution

The proposal is to implement CorDapp distribution service at the CorDapp level, for the following reasons:
* Ease of  integration with other BN services, such as [Business Network Membership Service](https://github.com/corda/corda-solutions/blob/master/bn-apps/memberships-management/design/design.md), [Ledger Synchronisation Service](https://github.com/corda/corda-solutions/blob/master/bn-apps/ledger-sync/design/design.md)
* Ability of BNs to tweak the implementation for their needs
* This approach would also ease an installation and integration of the BNO applications into the existing enterprise infrastructures, as it would require less integration points with not-flow based internal systems.

Why not to perform distribution on CZ level, via some centralised solution like Network Map or Doorman? Simply because some Business Networks might be uncomfortable with sharing their proprietary code with third party services.

#### CorDapps descriptors

Each CorDapp will be defined by a *CorDapp descriptor*. BNO will distribute a list of *CorDapp descriptors* via Corda flows (provided with the CDS implementation), which BN members **must** have installed to be able to transact on this BN.

```
{
  "cordapps" : [
    {
      "vendor" : "com.accenture",
      "name" : "accenture-states",
      "currentVersion" : "1.2",
      "minimumVersion" : "0.9",
      "hash" : "de3ba996a0876fb289b30400a166ad307dce3032c8714acaee977ac5543f7d0e",
      "vendorCertificate" : "vendor's certificate",
      "distribution" : { // specifies the way, how the CorDapp is distributed.
        "type" : "flows" // for example this application can be downloaded via Corda flows, which are provided with the CDS implementation
      }
    },
    {
      "vendor" : "com.accenture",
      "name" : "accenture-flows",
      "currentVersion" : "1.3",
      "minimumVersion" : "1.0",
      "hash" : "8261e230706962fe7a9a72bd73bb920ae263fce056bd400120602215357e62af",
      "vendorCertificate" : "vendor's certificate",
      "distribution" : {
        "type" : "web", // this CorDapp can be downloaded via HTTP
        "url" : "http://my-super-cdn"
      }
    },
    ...
  ]  
}
```

A *CorDapp descriptor* contains some generic information about a CorDapp, such as its `vendor`, `name`, `currentVersion`, `minimumVersion`, the `hash` of the jar file as well as the `vendorCertificate`, which the CorDapp jar can be validated with. `MinimumVersion` defines the minimum supported version of the CorDapp on this Business Network. `MinimumVersion` can be bumped by the BNO if a critical vulnerability has been discovered or if a new version of the CorDapp is not backward compatible.

#### Distribution mechanisms

Each CorDapp definition can be associated with a *distribution mechanism*. *Distribution mechanism* defines how a CorDapp **can** be downloaded, however it wouldn't enforce a node to perform any particular **action**. *Node administrators* will be able to specify what to do in a response to a new update availability or a minimum version bump. For example this could be such things as to send an email or to raise a warning on the monitoring system or to download a CorDapp into a local folder and etc. This configuration will be done on a *node level* (described in the further sections), as it might vary from a node to node even within a single BN. It's a responsibility of *node administrators* to configure their CDS CorDapps and to find the right balance between automation and their internal security policies. More about automations is described in the further sections.

*Distribution mechanisms* are defined by BNOs and should be matched 1-to-1 with the *download adaptors* on a node's side (described in the further sections) to automate downloading of CorDapp updates. Implementations of *download adaptors* should be provided by the BNO along with their CDS implementation. *Distribution mechanisms* will be pluggable and extensible. Each CorDapp can be associated with its own *distribution mechanism*, such as *Corda flows*, *http*, *ftp* and etc. BNOs will be able to define their own *distribution mechanisms* if they are missing from the standard implementation. *Distribution mechanisms* are uniquely defined by their *name* and might also have a custom configuration parameters, such as *download url*, *authentication parameters* and others.

#### Download adaptors

On a node's side, the *node administrator* will be able to associate a *distribution mechanism* (by its name) with a *download adaptor*. *Download adaptors* will have to implement a standard interface, which will be provided with the CDS. The *download adaptors* will be responsible for downloading a CorDapp based on its *distribution mechanism's* configuration, provided by the BNO, and the *local CDS configuration*, specified by the *node administrator*. The proposed *CDS configuration* structure:
```
{
  ...
  "distribution" : {

    "web" : {
      // This will associate MyCdnDownloadAdapter with the "web" distribution mechanism.  
      // MyCdnDownloadAdapter will be invoked against each CorDapp update with "web" distribution type.
      // MyCdnDownloadAdapter will be provided with the distribution mechanism's configuration (such as downloadUrl)
      // as well as with the download adaptor configuration, which is specified below
      "downloadAdaptor" : "com.my.adapters.MyCdnDownloadAdapter",

      "configuration" : { // this is the custom configuration, which will be passed to the MyCdnDownloadAdapter
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
If some *distribution mechanism* is not associated with any *download adaptor* - then no CorDapp updates with this *distribution mechanism* will be downloaded. *Node administrators* might choose to not to configure any *download adaptors* if they would like to perform download manually.

Jar signatures will be verified by default after a *download adaptor* finishes to download.

#### Update callbacks

*Node administrators* will be able to associate custom *update callbacks* with the events of the following types:
* `onUpdate`. The *update callback* will be invoked when a new update of a CorDapp is available.
* `onDeprecation`. The *update callback* will be invoked when an installed version of a CorDapp gets below the `minimumVersion` from the *CorDapp descriptor* for the first time.

*Update callbacks* will have to implement a standard interface, which will be provided with the CDS implementation.

*Update callbacks* might be responsible for doing such things as sending an email or dropping a message to MQ and etc. Developers will be able to implement their own *update callbacks* and to integrate them with their internal monitoring / alerting systems.

Proposed configuration for update callbacks:
```
{
  ...
  "callbacks" : {
    "onUpdate" : {
      "callback" : "com.my.adapters.SendEmailViaSMTPCallback",
      "configuration" : {
        // some custom configuration
      }
    },
    "onDeprecation" : {
      "callback" : "com.my.adapters.RaiseIssueOnMonitoringDashboardCallback",
      "configuration" : {
        // some custom configuration
      }
    }
  },
  ...
}
```

Similarly to *download adaptors*, *notification callbacks* might define their custom local configuration.

#### CorDapp structure

All CorDapps should contain a standard *CorDapp descriptor* inside their `META-INF` folder, with such information as *vendor*, *name* and *version*. This information will be used by the CDS CorDapp to match the installed CorDapps versions against the CorDapps descriptors received from the BNO.

CorDapps will have to be signed via standard [Jar Signing](https://docs.oracle.com/javase/tutorial/deployment/jar/signing.html) mechanism if their signature needs to be verified.

#### Distribution of initial software

To on-board onto a BN, a node will have to have such CorDapps as [Business Network Membership Service](https://github.com/corda/corda-solutions/blob/master/bn-apps/memberships-management/design/design.md) and CorDapp Distribution Service installed on it. Distribution of these CorDapps will have to be done either off-Corda, or via a standardised API which will have to be implemented by all BNs. Defining a standardised API is out-of-scope of this design, as its hardly feasible if doable at all.

The proposal of this design document - is to distribute the initial CorDapps *off-Corda*.

#### First start

On the first start, BN members will pull down a list of *CorDapp descriptors* from their BNO. 

![CorDapps list processing logic](./resources/cordapps_list_processing.png).

#### Notifying of new updates availability

BNO will notify BN members about availability of new updates. Notification will be send via Corda flows. Each notification will include a full list of updated CorDapps and their descriptors. Updates processing logic will be the same as described in the previous section.

BNO will not able to enforce a node to install updates. *Node administrators* should consider updating their CorDapps if a newer version is available and **must** update CorDapps if their version gets below the `minimumVersion`. Prompt installation can be achieved by utilising *download adaptors* and *update callbacks*, which have been described in the previous sections.

CorDapps should be designed to be backward compatible. Corda provides mechanisms for [flow versioning](https://docs.corda.net/head/upgrading-cordapps.html#flow-versioning), [contract and state upgrades](https://docs.corda.net/head/upgrading-cordapps.html#contract-and-state-versioning), [states evolution](https://docs.corda.net/head/serialization-default-evolution.html) and [contract constraints](https://docs.corda.net/head/api-contract-constraints.html) at the platform level out-of-the-box.

#### Installation of an update

*Node administrator* will be responsible for switching their node into *flows draining mode*, installing updates and restarting the node **manually**, in response to the notifications they received from the CDS.

#### Adding a new CorDapp update to a BNO's node

To distribute a new CorDapp update, BNO will need to:

* manually prepare and put the jar to a *distribution location*, according to the *distribution mechanism*, associated with the CorDapp. I.e. to copy to some folder on their filesystem, to upload to a CDN and etc.
* update the CorDapp's descriptor to point to the new *distribution location*. This will be done via Corda flow. The flow will update *CorDapp descriptors* as well as will send notifications about new update availability to the BN members.

Both of the steps can be triggered in an automated way from BNO's build pipeline if required.

Increment of a CorDapp's minimum version will be done via Corda flows as well, which will also send notifications about new update availability to all BN members.

The CDS implementation will **not** match *CorDapp descriptors* against the actual jar files in *distribution locations*. It will be a responsibility of a BNO's *node administrator* to keep those in sync.

### API extension points

* Custom *update callbacks* which can be integrated into internal systems
* Custom *download adaptors*
* Custom *distribution mechanisms*
