CorDapp Distribution Service
============================

CorDapp Distribution Service allows Corda network operators to distribute CorDapp updates to their network participants. Please see [this](./design/design.md) design doc for more details on the technical implementation.

CorDapp Distribution Service utilises Maven repositories for artifact distribution (as CorDapps are effectively fat-jars). Network participants periodically query a remote repository for updates and download them locally. Installation of updates is not automated yet. Corda node administrators need to stop / update / restart their nodes manually.  

CorDapp Distribution Service supports conventional HTTP(s) transport as well as bespoke Corda transports which are described in the further sections. 

## How to add CDS to your project

Add the following lines to `repositories` and `dependencies` blocks of your `build.gradle` file:

```
    repositories {
        maven {
          url 'https://ci-artifactory.corda.r3cev.com/artifactory/corda-solutions-releases'
        }
    }

    dependencies {
        cordapp "com.r3.businessnetworks:corda-updates-app:2.0"
        cordapp "com.r3.businessnetworks:corda-updates-states:2.0"
        cordapp "com.r3.businessnetworks:corda-updates-core:2.0"
        cordapp "com.r3.businessnetworks:corda-updates-transport:2.0"
    }
```

# Quickstart

To start using CorDapp Distribution Service please follow the steps below:

1. Create a folder for your local Maven repository and place `settings.conf` in there. Please see [this section](#hocon-configuration) for more information about supported configuration parameters.
2. Add CorDapps that you would like to synchronize to `settings.conf`, which is located in the root of the local repository created on the previous step (`~/.corda-updates/settings.conf` by default). Please refer to [this](#hocon-configuration) section for more details about the configuration file format.
3. Download `corda-updates-app` jar or build it by yourself. TODO: download link here
4. Install the CorDapp to the network participant's nodes and configure it as described [here](#participant-cordapp-configuration). `configPath` should point to the `settings.conf` created on the step #2.
5. If you are going to use [Corda transport](#corda-updates-transport) - then install the CorDapp to the repository hoster's node and configure it as described [here](#repository-hoster-cordapp-configuration).   
6. Schedule periodic synchronization by invoking [ScheduleSyncFlow](#scheduling-synchronisation) from the Corda shell of every participant node.
7. Done. When published, all updates will appear in the local repositories of participants. Please bear in mind that node administrators will still have to [install updates manually](https://docs.corda.net/releases/release-M8.2/creating-a-cordapp.html#installing-apps).

Please read through the rest of the document for more information about the operational considerations and the available configuration options.
 
# Structure

*CorDapp Distribution Service* consists of the following components:
* **corda-updates-core** - API over Maven Resolver.
* **corda-updates-transport** - custom transport implementation for Maven Resolver over Corda Flows.
* **corda-updates-app** - CorDapp that allows participants to schedule periodic synchronization and provides a basic functionality for version reporting.

# corda-updates-core

`corda-updates-core` sits in the heart of CorDapp Distribution Service and provides APIs on top of Maven Resolver to download CorDapps from remote Maven repositories over `file`, `http(s)` and `corda`  transports. `corda-updates-core` is used internally only and is not visible to the end users. It effectively allows to:
* Get a list of available artifact versions from a remote repository
* Download a single version of an artifact from a remote repository
* Download an artifact version range from a remote repository, which is effectively a combination of the two previous steps.
* Associate different CorDapps with different remote repositories and to sync them down via a single method invocation. 

`corda-updates-core` also supports basic authentication for HTTP proxies and remote repositories. 

# corda-updates-transport

`corda-updates-transport` provides a bespoke Maven Resolver transports over Corda Flows.

## Why do we need it?

Corda transport allows to transfer data over Corda Flows. It can be invoked from *inside a Corda node* only. 

Using this transport allow repository hosters to enforce their custom rules onto incoming requests and to filter out any unauthorised download attempts. For example CorDapp Distribution Service can be easily integrated with [Business Networks Membership Service](https://github.com/corda/corda-solutions/tree/master/bn-apps/memberships-management), that would effectively allow it to filter out any non Business Network traffic. 

Corda transport expects a remote repository URL specified in the format of `corda:x500Name/repositoryName`. `repositoryName` allows a repository hoster to serve artifacts from multiple remote repositories via the same node. `repositoryName` defaults to *"default"* if have not been explicitly provided. For example `corda:O=BNO,L=New York,C=US/default` (just imagine that you specify a Corda X500 name instead of a HTTP hostname).

## Access Control

Developers can implement their own access control by overriding `isSessionAllowed` method of `GetResourceFlowResponder`, `PeekResourceFlowResponder` and `ReportCordappVersionFlowResponder` classes. For more information about overriding flows please visit [this page](https://docs.corda.net/head/flow-overriding.html).

### Asynchronous invocations

Maven Resolver over Corda transport should always be invoked from a *non-Flow thread*. This is because, under the hood Corda transport starts a separate flow (which starts in a different from the calling thread) for data transfer to prevent Maven Resolver internals from being checkpointed as they are not `@Suspendable`. As Corda Open Source flows engine is single-threaded, invoking the transport synchronously would result into a deadlock, where the calling flow would be indefinitely waiting for the transport flow to finish while the transport flow would be indefinitely waiting for the calling flow to finish to be able to start.

*This behaviour is handled by CorDapp Distribution Service internally and is transparent to the end user.*  

# HOCON Configuration

CorDapp distribution service is configurable from a `HOCON` file, of the following structure

```hocon
# Path to the local repository
localRepoPath = "~/.corda-updates/repo"

# HTTP or HTTPS. Should be specified only if HTTP(S) proxy is used.
httpProxyType = "HTTP"

# Proxy host name. Should be specified only if HTTP(S) proxy is used.
httpProxyHost = "10.0.0.1"

# Proxy port. Should be specified only if HTTP(S) proxy is used.
httpProxyPort = "10009"

# Username for proxy authentication. Should be specified only if HTTP(S) proxy is used.
httpProxyUsername = "proxy_user"

# Password for proxy authentication. Should be specified only if HTTP(S) proxy is used.
httpProxyPassword = "P@$$w0rD"

# List of CorDapps to sync from remote repos. Can sync multiple CorDapps from multiple remote repositories
cordappSources = [
    {
        # URL of the remote repository to fetch the cordapps from
        remoteRepoUrl = "https://repo.maven.apache.org/maven2/"

        # List of the cordapps to sync from the remote repository. Should be specified in the form of "artifactGroup:artifactName"
        cordapps = ["net.corda:corda-finance"]

        # Username, if the remote repo requires basic HTTP authentication
        httpUsername = "repo_user"

        # Password, if the remote repo requires basic HTTP authentication
        httpPassword = "r3p0_P@$$"
    }
]
```
# corda-updates-app

`corda-updates-app`is a CorDapp that provides the following functionality:
* A scheduled state to periodically check for new updates availability
* Flows to schedule updates and to retrieve available CorDapp versions
* Flows to report information about CorDapp versions to the BNO

## Scheduling synchronisation

Updates can be scheduled via `ScheduleSyncFlow`. Synchronisation interval is driven by `syncInterval` configuration property and defaults to *once in 5 hours*. `ScheduleSyncFlow` can be started from Corda shell or via RPC. 

```kotlin
// synchronisation should be launched asynchronously if Corda transport is used 
subFlow(ScheduleSyncFlow(launchAsync = true))
```

> If Corda transport is not used, then ScheduleSyncFlow can be run in synchronous mode (for example if all remote repositories are configured to use -http or -file transports). Synchronous invocations are more convenient from a developer's perspective as results are available straight after the execution is finished. However, launching ScheduleSyncFlow in synchronous mode for Corda transport might result to a deadlock on a single-threaded flavours of Corda. Please refer to "Asynchronous invocations" section for more information about that.

## Getting available versions

Available versions can be fetched from `ArtifactsMetadataCache`. 

```kotlin
val artifactCache = serviceHub.cordaService(ArtifactsMetadataCache::class)
artifactsMetadataCache.cache // <-- list of the available artifacts will be here
```

> ArtifactsMetadataCache is populated by ScheduleSyncFlow. At least one synchronisation should pass in order for ArtifactsMetadataCache to contain any data. 

## Participant CorDapp Configuration

Configuration is loaded from `cordapps/config/corda-updates-app.conf` file in the node's folder.   

```
# path to the HOCON configuration file (described in the previous sections) 
configPath=./config.conf

# synchronisation interval in milliseconds defaults to 5 hours if not specified
syncInterval=18000000

# identity of the notary to use
notary="O=Notary,L=London,C=GB"

# identity of the BNO to report CorDapp versions to 
bno="O=BNO,L=London,C=GB"
```

## Repository Hoster CorDapp Configuration

Configuration is loaded from `cordapps/config/corda-updates-app.conf` file in the node's folder. Repository hoster node is required only if Corda transport is used.
```
# List of repositories to serve artifcats from. A requester is supposed to provide a name of repository to serve his request from. 
repositories {

  # "default" repository will be used if a repository name has not been provided by the requester
  default = "file:./LocalRepo"
  
  mavenCentral = http://repo1.maven.org/maven2/
  
  companyLocalRepo = ....
  
}
```

## Reporting CorDapp versions

Versions of installed CorDapps can be reported via `ReportCordappVersionFlow`. CorDapp Distribution Service doesn't collect information about installed CorDapps by itself. Instead, CorDapps are expected to report their versions by themselves. Version reporting can help BNOs to identify if any participants have an outdated versions of CorDapps installed and to contact them offline to ask to upgrade. 

BNOs can use `GetCordappVersionsFlow` and `GetCordappVersionsForPartyFlow` to query reported versions on their side.