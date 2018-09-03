![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

# CordApp distribution service (CDS)

DOCUMENT MANAGEMENT
---

## Document Control

| Title                | CordApp distribution service                             |
| -------------------- | ------------------------------------------------------------ |
| Date                 | 22 Aug 2018                                                |
| Author               | Ivan Schasny, Mike Hearn |
| Distribution         | Design Review Board, Product Management, Solutions Engineering, Platform Delivery |
| Corda target version | OS                                                   |

## HIGH LEVEL DESIGN

### Overview

This proposal describes the architecture of a reference implementation for a CorDapp Distribution Service (CDS), that would allow BNO to distribute CorDapps and their updates to the Business Network participants.

### Requirements

The following requirements have been gathered from various internal discussions within R3 and from [groups.io mailing lists](https://groups.io/g/corda-dev/message/190?p=,,,20,0,0,0::relevance,,updates+distribution,20,2,0,22686107):

* BNO should be able to notify BN members about new CorDapp version availability
* BNO should be able to revoke a specific CorDapp version from their BN
* BN members should be able to download a specific CorDapp version
* BNO should be aware of what version of their CorDapp(s) each BN member is running
* A node should be able to subscribe to a repository channel and be aware of it's synchronicity
* It should be possible to prevent CorDapp from working if a newer version is available
* CDS should support release channels
* CDS should be integratable into CI/CD pipelines
* BNO should be able to provide a textual description along with each CorDapp version

### Non-requirements:

* CDS will not provide functionality for automatic updates installation. Node administrator will still have to stop-upgrade-restart their nodes manually.
* CDS is not intended to be used to update the platform itself.
* How to design a CorDapp that supports upgrades is out of scope of this design document. Information about flow versioning, states evolution and contract constraints can be found in the [Corda Docs](https://docs.corda.net)
* Packaging of CorDapp. CDS is agnostic to it and will support multiple packaging formats.

### Target solution

#### General CorDapp distribution mechanism

The proposal is to distribute CorDapps via standard Maven repositories. This would allow BNOs to benefit from the existing rich Maven infrastructure. CorDapp distribution will be performed on per Business Network basis by BNO, who will need to host a Maven repo as a part of their infrastructure. BNOs will be able to choose from a variety of open source repositories that are available on the market. Also, usually corporates already have Maven repositories running as a part of their software stack anyway, so they are expected to be familiar with the process.

[Maven Resolver](https://wiki.eclipse.org/Aether) will be used on the client side as a library for programmatic dependency resolution. Maven Resolver supports pluggable transports and is shipped with `HTTP(s)` transport available out-of-the-box. To ease an integration into the existing enterprise infrastructures, the proposal is to provide a bespoke implementation of *Maven transport over Corda flows*. This would allow corporates to deploy CDS on-premises without having to reconfigure their firewalls to allow extra HTTP traffic.

CDS will support standard Maven Artifact naming notation `<groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>`. CDS will utilise Maven classifiers that will allow publishing CorDapps targeting different hardware / software configurations.

CDS will not initially support a concept of *release channels*. Release channels can be simulated by using multiple Maven repositories (a repo per channel). Users can sync different remote repositories to different local locations and then manually install a desired version of CorDapp to their nodes. Users will have to have a node-per-environment anyway, so this shouldn't become a problem .

Reusing Maven infrastructure will also enable a seamless integration into the existing CI/CD pipelines.

However, Maven doesn't support some of the required features, such as realtime notifications, revocations, version reporting and etc. To address these requirements CDS will be shipped with a CorDapp that will provide the above-mentioned functionality on top of Maven Resolver.

CDS will be shipped as 2 components:
* **CDS library (cds-lib)** - a wrapper around Maven Resolver, that will be handling all heavy-lifting, such as artifact resolution and downloading. The library will also include custom transport implementations.
* **CDS CorDapp (cds-cordapp)** - a CorDapp that will provide scheduling, notification, revocation and reporting functionality on top of the `cds-lib`.

CDS can be used as a CorDapp or as a standalone library. High level architectures are outlined below.

##### CDS as a CorDapp with a transport over HTTP
![CDS as CorDapp](./resources/cds-as-cordapp-http.png)

##### CDS as a CorDapp with a transport over Corda Flows
![CDS as CorDapp](./resources/cds-as-cordapp-flows.png)

##### CDS as a library over HTTP
![CDS as library](./resources/cds-as-library-http.png)

##### CDS as a library over Corda RPC
![CDS as library](./resources/cds-as-library-rpc.png)

There are no strict limitations around following one architecture or the other. BNs can mix and match depending on their requirements. While HTTP transport might seem to be an easier option, using Corda flows have a couple of benefits over it, in particular:
* BNO will be able to host Maven in a private segment of their network, without exposing it to internet, effectively hidden behind firewalls and Corda.
* BNO will be able to expose the repo to their BN members only by integrating `cds-cordapp` with Business Network Management Service.

##### cds-lib

`cds-lib` will be used to pull down artifacts from a remote repository. The library will be embeddable into a third-party software or usable as a standalone from a command line. This will give BNs flexibility to utilise CDS in the best way to fit their requirements.

`cds-lib` will be configurable via system properties and via external configuration file.

`cds-lib` will support the following transports:
* *HTTP(s)*. Available in Maven Resolver out-of-the-box with proxy- and repository- level authentications support.
* *Corda Flows*. Transport implementation that allows transferring files over Corda Flows.
* *Corda RPC*. Its essentially the same as *Corda Flows*, with the difference that the *RPC* version will be used if `cds-lib` is invoked from outside of a Corda node.

Preferred transport will be overridable via custom property. *HTTP(s)* transport will be used by default.

`cds-lib` is CorDapp agnostic and can be used to pull down any type of artifacts from any Maven2 compatible repository.

##### cds-cordapp

`cds-cordapp` will provide the following functionality on top of the `cds-lib`:

* Scheduled state for BN Members to periodically sync down their local CorDapp repositories with the BNO repository.
* Flows for BNO to notify BN members about new CorDapp version availability. BN members will be able to setup custom integration hooks via API extensions points, such as to send an email, download a CorDapp and etc. The reference implementation will include at least one hook to automatically download CorDap via `cds-lib`.
* Flows for BNO to notify BN members about CorDapp version revocations. If a version of CorDapp was revoked, BN members are expected to manually update their nodes to the last not-revoked version ASAP.
* Flows for BNO to collect reports from BN members about CorDapp versions installed on their nodes. Only versions of CorDapps related to *this* Business Network should be reported. CDS will rely on the information provided in CorDapp `MANIFEST` files until a better API is available.
* Flows for BN members to manually request a list of revoked CorDapp versions from BNO.
* Flows to optionally notify members if have not latest or revoked versions of CorDapps installed on their nodes.
* Flows for members to fetch a list of available CorDapps versions from BNO.

Under the hood, `cd-cordapp` will be calling `cds-lib` for all Maven-related interactions.

In the Business Networks where members host nodes by themselves, BNO can only do their best to encourage the members to upgrade by notifying them via CDS or sending them an email, but ultimately it will be up to a member to decide on whether they would like to upgrade or not. Members with a stale CorDapp versions might loose their ability to transact on the BN if the CorDapp is not backwards compatible.

CorDapps can be designed in the way that they stop working if a newer version of the CorDapp is available. This can be done by making CorDapps flows to compare the current CorDapp version against the list of all available versions from the BNO. If the current version is not the latest - the flows might refuse to start. It will be up to the CorDapp developers to use such technics if they need to.

CDS will not provide any automations around database or environment evolution. These procedures should be defined by a CorDapp vendor.

Description of an update can be distributed along with the notifications or packaged into the artifact itself.

##### Using cds-lib as a standalone

`cds-lib` will be invokable from a command line. CLI invocation could look like:

```
# Will pull down a single version of cordapp and its dependencies
> java -jar cds-lib.jar
    -Dcordapp="com.my.company.name:corda-name:1.0"
    -DremoteRepo="https://my-company-repo.com"
    -DlocalRepo="/path/to/my/local/repo"
    -Dtransport="rpc"
    -Dconfiguration="config.properties"

# Will pull down all versions of cordapps and their dependencies
> java -jar cds-lib.jar
    -Dcordapp="com.my.company.name:corda-name:[0,)"
    -DremoteRepo="https://my-company-repo.com"
    -DlocalRepo="/path/to/my/local/repo"
    -Dtransport="http"
    -Dconfiguration="config.properties"

```

Command line parameters will take precedence over the configuration file.

Exact configuration parameters will be documented during the implementation phase.

#### Implementation details

The proposed solution will require implementation of the following components:

* Custom Maven Resolver transports over flows and RPC. This will require the following interface to be implemented, which should be fairly straightforward to do:
```
public interface Transporter
    extends Closeable
{
    int classify( Throwable error );
    void peek( PeekTask task ) throws Exception;
    void get( GetTask task ) throws Exception;
    void put( PutTask task ) throws Exception; // Not required
    void close();
}
```
* Wrapper around Maven Resolver, with support of CLI interface and custom configuration parameters.
* `cds-cordapp` with the functionality, described in the previous sections.

#### API extension points

* TODO
