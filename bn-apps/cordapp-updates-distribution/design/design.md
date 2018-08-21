![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

# CordApp distribution service (CDS)

DOCUMENT MANAGEMENT
---

## Document Control

| Title                | Ledger Synchronisation Service                            |
| -------------------- | ------------------------------------------------------------ |
| Date                 | 17 May 2018                                                |
| Author               | Ivan Schasny, Mike Hearn |
| Distribution         | Design Review Board, Product Management, Solutions Engineering, Platform Delivery |
| Corda target version | OS                                                   |

## HIGH LEVEL DESIGN

### Scope

The following goals have been identified:

* Allow BNO to distribute CorDapp(s) updates to the BN.
* Allow BNO to promptly notify BN members about such events as new update availability and CorDapp version revocations.
* Give BNO visibility over what CorDapp(s) versions BN members have installed on their nodes.

Non-goals:
* Automatic update installation. Node administrators will need to stop-update-restart their nodes manually.

### Goals

The following requirements have been gathered from various internal discussions within R3 and from `groups.io` [mailing lists](https://groups.io/g/corda-dev/message/190?p=,,,20,0,0,0::relevance,,updates+distribution,20,2,0,22686107):

* Notification of new CorDapp version availability
* Revocation of a specific CorDapp version due to a critical bug or vulnerability
* Download of a specific version of CorDapp
* BNO should be aware of what version of the CorDapp each BN member is running
* A node should be able to subscribe to a repository channel and be aware of it's synchronicity
* CorDapp may refuse to work if out of Sync
* Release channels
* Should be integratable into CI/CD pipelines
* Should support update descriptions
* Should support update importance. The critical updates might require notification to be sent to the operator.

### Non-goals:

* CDS will not provide functionality for automatic update installation. Node administrator will still have to stop-update-restart their nodes manually.
* CDS is not intended to be used to update the platform itself.
* How to design a CorDapp to support upgrades is out of scope of this design document. Information about flow versioning, states evolution and contract constraints can be found on [Corda Docs](https://docs.corda.net)

### Target solution

#### General CorDapp distribution mechanism

The proposal is to distribute CorDapps via standard Maven repository mechanism. This would allow BNOs to benefit from the existing rich Maven infrastructure. CorDapp distribution will be performed on per Business Network basis. BNOs will need to host a Maven repo as a part of their infrastructure. This is not envisaged to become a bottleneck: a lot of open source repositories are available on the market and usually corporates already have Maven repositories running as a part of their software stack, so they are expected to be familiar with the process.

[Maven Resolver](https://wiki.eclipse.org/Aether) will be used on the client side as a library for programmatic dependency resolution. Maven Resolver supports pluggable transports and is shipped with `HTTP(s)` transport available out-of-the-box. To ease an integration into the existing enterprise infrastructures, the proposal is to provide a bespoke implementation of *Maven transport over Corda flows*. This would allow corporates to deploy CDS on-premises without having to reconfigure firewalls to allow HTTP traffic.

CDS will support standard Maven Artifact naming notation `<groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>`. CDS will utilise Maven classifiers that will allow publishing CorDapps targeting different hardware / software configurations.

CDS will not initially support a concept of release channels. Release channels can be simulated by using multiple Maven repositories (a repo per channel). Users can sync different remote repositories to different local locations and then manually install a desired version of CorDapp to their node.

Reusing Maven infrastructure will also enable a seamless integration into the existing CI/CD pipelines.

However, Maven doesn't support some of the required features, such as realtime notifications, revocations, version reporting and some others. To address these requirements CDS will be shipped with a CorDapp that will provide the above functionality on top of Maven Resolver.

CDS will be shipped as 2 components:
* **CDS library (cds-lib)** - wrapper around Maven Resolver, which will be handling all heavy-lifting, such as artifact resolution and downloading. The library will also include custom transport implementations.
* **CDS CorDapp (cds-cordapp)**, which will provide scheduling, notification, revocation and reporting functionality on top of the `cds-lib`.

CDS can be used as a CorDapp or as a standalone library. High level architectures are outlined below.

**CDS as a CorDapp**
![CDS as CorDapp](./resources/cds-as-cordapp.png)

**CDS as a library**
![CDS as library](./resources/cds-as-library.png)

There are no strict limitations on following one architecture or the other. BNs can mix and match depending on their requirements. For example BNO might prefer to notify BN members via email and then let everyone to pull down the latest versions manually.

##### cds-lib

`cds-lib` will be used to sync down one or more versions of CorDapp(s) from a remote repository. The library will be embeddable into third-party software or usable as a standalone from a command line. This will give BNs flexibility to utilise the service in the best way to fit their requirements.

`cds-lib` will be configurable via system properties and via external configuration file.

`cds-lib` will support the following transports:
* *HTTP(s)*. Available in Maven Resolver out-of-the-box with proxy- and repository- level authentications support.
* *Corda Flows*. Will be used if `cds-lib` is invoked from within Corda Node.
* *Corda RPC*. Its essentially the same as *Corda Flows*, with the difference that the *RPC* version will be used if `cds-lib` is invoked from outside of a Corda node.

Preferred transport will be overridable via custom property. *HTTP(s)* transport will be used by default.

`cds-lib` is CorDapp agnostic and can be used to resolve regular artifacts.

##### cds-cordapp

`cds-cordapp` will provide the following functionality on top of the `cds-lib`:

* Scheduled state for BN Members to periodically sync down their local CorDapp repositories with the BNO repository.
* Flows for BNO to notify BN members about new CorDapp version availability. BN members will be able to setup custom integrations hooks via API extensions points, such as to send an email, download a CorDapp and etc.
* Flows for BNO to notify BN members about CorDapp version revocations. If a version of CorDapp was revoked, BN members are expected to manually update their nodes with the latest not-revoked version ASAP.
* Flows for BNO to collect reports from BN members about CorDapp versions installed on their nodes. Only versions of CorDapps related to this Business Network should be reported. CDS will rely on the information provided in CorDapp `MANIFEST` file.
* Flows for BN members to manually request a list of revoked CorDapp versions from BNO.
* Flows to optionally notify member in the case if not latest / revoked version of CorDapp is installed on their node.

Under the hood, the CorDapp will be calling `cds-lib` for all Maven-related interactions.

`cds-cordapp` will be relying on manually scanning CorDapp MANIFEST files until a better API is available.

Full Maven coordinates will be used for all CorDapp related notifications, i.e. new version availability or revocation.  

In the Business Networks where members host nodes by themselves, BNO can only do their best to encourage the members to upgrade by notifying them via CDS or sending them an email, but ultimately it will be up to a member to decide on whether they would like to upgrade or not. Members with stale CorDapp versions might loose their ability to transact on the BN if the CorDapp is not backwards compatible.

CDS will not provide any automations around database or environment evolution. These procedures should be defined by a CorDapp vendor.

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

More configuration parameters will be added and documented during the implementation.

#### Implementation details

The proposed solution would require implementation of the following components:

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
* CorDapp with the flows, specified in the previous sections.

#### API extension points

* TODO
