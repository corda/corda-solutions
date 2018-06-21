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
* First installation of a node. This procedure would vary from a case to case, depending on such things as:
  * if a user is going to run a node by himself or someone will be running it on his behalf.
  * if its Corda OS or Corda Enterprise.
  * On-premises deployment or cloud
  * HA
  * and others...

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

On start, each node should pull down a list of CorDapps required to transact on the network. This list will be distributed by BNO via Corda flows. BNO will be notifying each member about every change to the list. The proposed data structure:

```
{
  "lastUpdated" : "2018-06-20T13:59:58+00:00",
  "cordapps" : [
    {
      "cordapp" : {
          "vendor" : "accenture",
          "name" : "accenture-states",
          "currentVersion" : "1.2",
          "minimumVersion" : "0.9",
          "hash" : "aldsdjfsjdfjksdf",
          "vendorCertificate" : "blahblahblah"
      },
      "distribution" : { // specifies the way, how the CorDapp is distributed.
        "type" : "flows" // for example this application can be downloaded via bundled Corda Flows
      }
    },
    {
      "cordapp" : {
          "vendor" : "accenture",
          "name" : "accenture-flows",
          "currentVersion" : "1.3",
          "minimumVersion" : "1.0",
          "hash" : "jfbasvdbascdbvac",
          "vendorCertificate" : "blahblahblah"
      },
      "distribution" : {
        "type" : "web", // this CorDapp can be downloaded via HTTP
        "url" : "http://my-super-cdn"
      }
    }
  ]  
}
```

The `cordapps` section defines the full list of CorDapps which are required to be installed on a node for it to be able to transact on the BN. Each CorDapp definition can be associated with a *distribution mechanism*. *Distribution mechanism* defines how a CorDapp **can** be *downloaded*, however it doesn't enforce a node to perform any particular **action**. *Distribution mechanisms* are described in details in the further sections. *Node administrators* will be able to specify what to do in response to a new update or a minimum version bump. For example it can be such things as to send an email or to raise a warning on the monitoring system or to download a CorDapp into a local folder and etc. This configuration will be done on a *node level* (described in the further sections), as it might vary from a node to node even within a single BN. Downloading the updates doesn't *have to* be performed in an automated way. It will be up to *node administrators* to configure their CDS CorDapps to find the right balance between automation and their organisation security policies. The how-to-do is described in the further sections.

`Cordapp` descriptor contains information about a CorDapp, such as *vendor*, *name*, *currentVersion*, *minimumVersion*, the *hash* of the jar file as well as the  *vendor's certificate*, which the jar can be validated with. *Minimum version* defines the minimum supported version of the CorDapp on this Business Network. *Minimum version* can be bumped by the BNO if a critical vulnerability has been discovered or if a new version of the CorDapp is not backward compatible.

Given a nature of the DLT applications, deploying an update to the whole network in the same time might not be feasible, unless the whole of the network can be shut down to perform an upgrade. Shutting the whole of the network down is possible, but highly unlikely, as a single node might be involved into multiple Business Networks, with different governance structures. BNO will not able to enforce a node to install updates. Its a responsibility of the node owner to install updates promptly if their CorDapps are outdated and especially if a CorDapp's version is below the allowed minimum. The automations are described in the further sections.

CorDapps should be designed to be backward compatible. Corda provides mechanisms for flow versioning [LINK] and contract constraints [LINK] at the platform level out-of-the-box.

#### Distribution mechanisms

*Distribution mechanisms* will be pluggable and extensible. Each CorDapp can be associated with a separate *distribution mechanism*, such as *Corda Flows*, *HTTP* or *FTP* and others. BNOs will be able to implement a new *distribution mechanism* if its missing from the standard implementation. Distribution via Corda Flows will be provided along with the initial reference implementation.

As was mentioned before, *node administrators* will be able to specify what to do in response to a new update availability. This configuration will be done at the CorDapp level. Proposed configuration structure:

```
{
  "distribution" : { // configures how to download updates
    "web" : {
      "downloadAdaptor" : "com.my.adapters.MyCdnDownloadAdapter", // which adapter to use to download from web
      "configuration" : {
        "authentication" : { },
        "downloadFolder" : "/home/corda/updates/",
      },
    },
    "flows" : {
      "downloadAdapter" : "com.my.adapters.DoNothingAdapter", // don't download updates automatically
      "configuration" : {
        "downloadFolder" : "/home/corda/updates/",
        // some custom configuration here
      },
    }
  },
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
  }
}
```





### API extension points

* Integration with monitoring systems to report about missing transactions
* Mechanisms, preventing a node from transacting while the synchronisation is in progress
* BNO's signed permissions (tickets) for data recovery






* Each BN member would download the cordapp list when their node starts.
* BNO would notify all members every time the list changes, i.e. when a new update is available or some version of a cordapp gets deprecated. As the list is fairly small, the whole of it can be distributed along with each message.
* Every time the list changes, each member's responding flows would scan locally installed cordapps and would verify them against the deprecated list. If any of the locally installed cordapps happens to be deprecated, it should fire a notification to the node administrator (raise an alert on monitoring dashboard, send an email or something else)
* Similarly to *download adapters*, *notification adapters* would also be pluggable. We can include "send a notification via SMTP adapter" by default and also provide tutorials about how users can implement their own notification adapters.
* Node administrators would be able to configure behaviour of their applications. The configuration might look like:

{
  "distribution" : { // configures how to download updates
    "web" : {
      "downloadAdapter" : "com.my.adapters.MyCdnDownloadAdapter", // which adapter to use to download from web
      "configuration" : {
        "authentication" : {
          // custom authentication parameters here
        },
        // some other custom configuration parameters
      },
    },
    "flows" : {
      "downloadAdapter" : "com.my.adapters.DoNothingAdapter", // don't download updates automatically
      "configuration" : {
        // some custom configuration here
      },
    }
  },
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
  }
}

* Node administrators should follow standard DevOps procedures to install the updates, such as switch the node into flows draining mode, wait until all flows finish, copy the jars to cordapps folder and and etc.
* As installing cordapp update involves some manual work to be done by a node administrator, my proposal is to not to worry about automation of it for now.

Issues:

* BNO node might get overloaded if everyone would start downloading updates in the same time and hence wouldn't be able to perform their BAU tasks. Possible resolutions:
  - To distribute applications via CDN
  - To distribute updates via flows only inside the system maintenance window, when volume of transaction is envisaged to be low (i.e. weekends).
* CZ enforces max size constraint on messages which can be sent via flows. Large cordapps would have to be split into multiple chunks in order to be distributed via flows.
