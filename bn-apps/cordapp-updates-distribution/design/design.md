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

This proposal describes the architecture of a reference implementation for a CorDapp Distribution Service (CDS), that would allow BNOs to distribute updates to CorDapps to Business Network participants. This design targets scenarios when Business Network members maintain their nodes by themselves and can decide on what version of a CorDapp they would like to upgrade to. This design does *not* target scenarios where upgrade function is centralised and the whole network can be shut down to perform stop-upgrade-restart.

### Requirements

The following requirements have been gathered from various internal discussions within R3 and from [groups.io mailing lists](https://groups.io/g/corda-dev/topic/22686107#190), which is recommended for reading prior to the design.

* BNO should be able to notify members about new CorDapp version availability
* BNO should be able to revoke a specific version of CorDapp from their BN
* A member should be able to download a specific version of a CorDapp
* BNO should be able to collect a report from members about CorDapp versions installed on their nodes
* Members should be able to sync their local repositories with the remote
* It should be possible to prevent a CorDapp from working if a newer version is available
* CDS should support release channels
* CDS should be integratable into CI/CD pipelines
* BNO should be able to provide a textual description along with each CorDapp version
* Members should be aware of current and revoked versions of CorDapps

### Non-requirements:

* CDS will not provide functionality for automatic updates installation. Node administrator will still have to stop-upgrade-restart their nodes manually.
* CDS is not intended to be used to update the platform itself.
* How to design a CorDapp that supports upgrades is out of scope of this design document. Information about [flow versioning](https://docs.corda.net/upgrading-cordapps.html#flow-versioning), [states evolution](https://docs.corda.net/upgrading-cordapps.html#contract-and-state-versioning) and [contract constraints](https://docs.corda.net/api-contract-constraints.html) and more can be found in the [Corda Docs](https://docs.corda.net)
* Packaging of a downloadable. CDS is agnostic to it and will support multiple packaging formats. However CDS expects CorDapps to contain a default metadata such as `vendor`, `name` and `version` in their `MANIFEST` file. Such metadata is automatically added by the `cordapp` plugin during CorDapp packaging.
* CDS will not provide any automations around database or environment evolution. These procedures should be defined separately by a CorDapp vendor.

### Target solution

#### General CorDapp distribution mechanism

The proposal is to distribute CorDapps via Maven repositories. This would allow to benefit from the existing rich Maven infrastructure, such as a number of open source repositories that are available on the market, established artifact distribution protocols, dependency resolution, seamless integration into CI/CD pipelines and others. Maven repositories can be hosted and maintained by BNOs, as they already have some administrative responsibilities and are a natural fit for such task. Maven repositories are also usually familiar to corporates, who already run them as a part of their infrastructures.

#### CDS Implementation

[Maven Resolver](https://wiki.eclipse.org/Aether) will be utilised as a library for programmatic dependency resolution. Maven Resolver supports pluggable transports and is shipped with `HTTP(s)` available out-of-the-box. To ease an integration into the existing enterprise infrastructures, the proposal is to provide a bespoke implementation of *Maven transport over Corda flows*. This would allow corporates to deploy CDS on-premises without having to reconfigure their firewalls to allow extra HTTP traffic.

However, Maven doesn't support some of the required features, such as notifications, revocations, version reporting and etc. To address these requirements CDS will be implemented as two components:

* **CDS library (corda-updates-core)** - a wrapper around Maven Resolver, that will be handling all Maven-related interactions, such as artifact resolution, downloading and others. `corda-updates-core` will include custom transport implementations and will be usable as a library or from a command line.
* **CDS CorDapp (corda-updates-app)** - a CorDapp that will provide scheduling, revocation and reporting functionalities on top of the `corda-updates-core`.

> CDS won't natively support *release channels*. Release channels can be simulated by using multiple Maven repositories (a repo per channel). Users can sync down different remote repositories to different local locations and then manually install a desired version of CorDapp to their nodes.

##### corda-updates-core

`corda-updates-core` is a convenience wrapper around Maven Resolver. It will support full Maven coordinates, in the format of `<groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>` as well as version ranges. Version ranges will be specifiable in mathematical range notation, i.e. "[1.0,2.0)", "[1.0,)" or "[1.0]". Range queries are supported by Maven Resolver out-of-the-box and will not require extra development efforts. `corda-updates-core` will allow to:
* Fetch metadata about a single or a range of versions from a remote Maven repository.
* Download a single or a range of versions from a remote repository. `corda-updates-core` will be packaging-type agnostic and can be used to pull down any type of artifacts from any Maven2 compatible repository. Support of classifiers will allow devs to publish CorDapps targeting different hardware / software configurations.

`corda-updates-core` will support pluggable transports:
* *HTTP(s)*. Available in Maven Resolver out-of-the-box with proxy- and repository- level authentications support.
* *Corda Flows*. Transport implementation that allows transferring files over Corda Flows.

`corda-updates-core` will be invokable from a command line, with command line parameters taking precedence over the configuration file. CLI invocation *might* look like:

```
# Will pull down all versions of cordapps and their dependencies
> java -jar corda-updates-core.jar
    -Dcordapp="com.my.company.name:corda-name:[0,)"
    -DremoteRepo="https://my-company-repo.com"
    -DlocalRepo="/path/to/my/local/repo"
    -Dconfiguration="config.properties"

```

Exact configuration parameters will be documented during the implementation.

##### corda-updates-app

`corda-updates-app` will provide the following functionality on *top* of the `corda-updates-core`:
* Scheduled state for members to periodically sync their local repos with a single remote repository.
* Flows for BNO to collect reports from members about CorDapp versions installed on their nodes. Only versions of CorDapps related to *this* Business Network should be reported. CDS will rely on the information provided in CorDapp `MANIFEST` files until a better API is available. With the future support for the *package namespace ownership*, CDS will report only cordapps that are a part of this particular Business Network.
* Flows for members to manually query a list of revoked versions from BNO. Revocations will contain full Maven coordinates + optional description (reasons for revocation). If a version of CorDapp was revoked, members are expected to manually update their nodes to the last not-revoked version ASAP.
* Flows for members to get a list of available CorDapp versions, which is essentially contents of the local cache.
* Corda shell extensions to query a list of available / revoked versions or to trigger a sync manually.

> It's important to emphasise, that in the Business Networks where members host nodes by themselves, BNO can only *do their best* to encourage the members to upgrade by notifying them via CDS or sending them an email. Ultimately it will be up to a member to decide on whether they would like to upgrade or not. It should be in a member's best interest to promptly upgrade as otherwise they might lose an ability to transact if a CorDapp is not backwards compatible.

> CorDapps can be designed to stop working if a newer version is available. This can be done by making flows to compare the current version against the list of available versions from CDS. If the current version is not the latest - the flows might refuse to start. It will be up to the CorDapp developers to utilise such techniques.

`corda-updates-app` will not include functionality to notify about a new update availability or a version revocation. Such information can be distributed via emails instead, as installation procedure requires manual intervention (stop-update-restart) anyway. This might be improved in the future, when hot reloading of cordapps is supported.

#### High level architecture diagrams

CDS can be used as a CorDapp or as a standalone library, with pluggable transports over HTTP or Corda flows. Sample component architectures can be found below:

##### CDS as a CorDapp with a transport over HTTP

![CDS as CorDapp](./resources/cds-as-cordapp-http.png)

##### CDS as a CorDapp with a transport over Corda Flows
![CDS as CorDapp](./resources/cds-as-cordapp-flows.png)

There are no strict limitations around following one architecture or the other. BNs can mix and match depending on their requirements. While HTTP transport might seem to be an easier option, using Corda flows have a couple of benefits over it, in particular:
* BNO will be able to host Maven in a private segment of their network, without exposing it to internet, effectively hidden behind firewalls and Corda.
* BNO will be able to expose the repo to their BN members only by integrating `corda-updates-app` with Business Network Management Service.

#### Implementation details

The proposed solution will require implementation of the following components:

* Custom Maven Resolver transports over flows. This will require the following interface to be implemented, which should be fairly straightforward to do:
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
We might also have to fork Maven Resolver and add `@Suspendable` annotations to it (requires PoC). This problem will go away with Java 9.
* Wrapper around Maven Resolver, with support of CLI interface and custom configuration parameters.
* `corda-updates-app` with the functionality, described in the previous sections.
