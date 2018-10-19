CorDapp Distribution Service
============================

CorDapp Distribution Service allows Corda network operators to distribute CorDapp updates to their network participants. Please see [this](./design/design.md) design doc for more details on the technical implementation.

CorDapp Distribution Service utilises Maven repositories for artifact distribution (as CorDapps are effectively fat-jars). Network participants periodically query a remote repository for updates and download them locally. Installation of updates is not automated yet. Corda node administrators need to stop / update / restart their nodes manually.  

CorDapp Distribution Service supports conventional HTTP(s) transport as well as bespoke Corda transports which are described in the further sections. 

# Quickstart

To start using CorDapp Distribution Service please follow the steps below:

1. Download `corda-updates-shell` jar or build it by yourself. TODO: download link here
2. Initialise a local repository via `java -jar corda-updates-shell-xxx.jar --mode=INIT`. Please see the [usage](#usage) section for more information about the supported parameters. By default, the local repository will be created under `~/.corda-updates` folder. 
3. Add CorDapps that you would like to synchronize to `settings.conf`, which is located in the root of the local repository created on the previous step (`~/.corda-updates/settings.conf` by default). Please refer to [this](#hocon-configuration) section for more details about the configuration file format.
4. Download `corda-updates-app` jar or build it by yourself. TODO: download link here
5. Install the CorDapp to the network participant's nodes and configure it as described [here](#participant-cordapp-configuration). `configPath` should point to the `settings.conf` created on the step #2.
6. If you are going to use [Corda-based transports](#corda-updates-transport) - then install the CorDapp to the repository hoster's node and configure it as described [here](#repository-hoster-cordapp-configuration).   
7. Schedule periodic synchronization by invoking [ScheduleSyncFlow](#scheduling-synchronisation) from the Corda shell of every participant node.
8. Done. When published, all updates will appear in the local repositories of participants. Please bear in mind that node administrators will still have to [install updates manually](https://docs.corda.net/releases/release-M8.2/creating-a-cordapp.html#installing-apps).

Please read through the rest of the document for more information about the operational considerations and the available configuration options.
 
# Structure

*CorDapp Distribution Service* consists of the following components:
* **corda-updates-core** - API over Maven Resolver.
* **corda-updates-transport** - custom transport implementations for Maven Resolver over Corda Flows.
* **corda-updates-app** - CorDapp that allows participants to schedule periodic synchronization and provides a basic functionality for version reporting.
* **corda-updates-shell** - command line interface.

# corda-updates-core

`corda-updates-core` sits in the heart of CorDapp Distribution Service and provides APIs on top of Maven Resolver to download CorDapps from remote Maven repositories over `file`, `http(s)`, `corda-flows` and `corda-rpc` transports. `corda-updates-core` is used internally only and is not visible to the end users. It effectively allows to:
* Get a list of available artifact versions from a remote repository
* Download a single version of an artifact from a remote repository
* Download an artifact version range from a remote repository, which is effectively a combination of the two previous steps.
* Associate different CorDapps with different remote repositories and to sync them down via a single method invocation. 

`corda-updates-core` also supports basic authentication for HTTP proxies and remote repositories. 

# corda-updates-transport

`corda-updates-transport` provides a bespoke Maven Resolver transports over Corda Flows and Corda RPC.

## Why do we need it?

Corda-based transports allow repository hosters to enforce their custom rules onto incoming requests and to filter out any unauthorised download attempts. For example CorDapp Distribution Service can be easily integrated with [Business Networks Membership Service](https://github.com/corda/corda-solutions/tree/master/bn-apps/memberships-management), that would effectively allow it to filter out any non Business Network traffic. 

## Session Filters

Corda-based transports allow developers to implement their custom `SessionFilter`s that can be used to reject any unintended download requests. `SessionFilter` is a simple interface that, if implemented, is invoked against every incoming download request. `SessionFilter` returns a boolean that indicates whether the request should be let through or not.

```kotlin
interface SessionFilter {
    @Suspendable
    fun isSessionAllowed(session : FlowSession, flowLogic : FlowLogic<*>) : Boolean
}
```
For example a session filter that would allow only the Business Network traffic through can be implemented in the following way using [Business Network Membership Service](https://github.com/corda/corda-solutions/tree/master/bn-apps/memberships-management):

```kotlin
class BusinessNetworkSessionFilter : SessionFilter {

    // GetMembershipsFlow is provided as a part of Business Network Membership Service implementation
    @Suspendable
    override fun isSessionAllowed(session : FlowSession, flowLogic : FlowLogic<*>) = flowLogic.subFlow(GetMembershipsFlow())[session.counterparty] != null
}
```

## Transport modes

The following transports are supported:
* **corda-flows** allows to transfer data over Corda Flows. It can be invoked from *inside a Corda node* only. 
* **corda-rpc** allows to transfer data over Corda RPC. It reuses the same flows as `corda-flows` transport, but invokes them via RPC instead.  
* **corda-auto** is an automatic switch between `corda-rpc` and `corda-flows` transports. The underlying transport is chosen based on the value of `corda-updates.mode` custom Maven Resolver session property, that is set in the runtime based on the invocation context. The main purpose for this mode - is to allow Cordapp Distribution Service to reuse the same configuration file, regardless of whether it was invoked from inside or outside of a Corda node. 

Corda-based transports expect a remote repository URL to be specified in the format of `transport-name:x500Name`. For example `corda-auto:O=BNO,L=New York,C=US` (just imagine that you specify a Corda X500 name instead of a HTTP hostname).

**corda-auto** is the recommended way of using Corda-based transports.

### Asynchronous invocations

Maven Resolver over Corda-based transports should always be invoked from a *non-flow thread* when used *inside Corda Flows*. This is because, under the hood Corda-based transports start a separate flow (which starts in a different from the calling thread) for data transfer to prevent Maven Resolver internals from being checkpointed as they are not `@Suspendable`. As Corda OS flows engine is single-threaded, invoking the transports synchronously would result into a deadlock, where the calling flow would be indefinitely waiting for the transport flow to finish while the transport flow would be indefinitely waiting for the calling flow to finish to be able to start.

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

# RPC host of a Corda node to connect to. Should be specified only if corda-rpc or corda-auto transport is used.
rpcHost = "localhost"

# RPC port of a Corda node to connect to. Should be specified only if corda-rpc or corda-auto transport is used.
rpcPort = "8003"

# RPC username of a Corda node to connect to. Should be specified only if corda-rpc or corda-auto transport is used.
rpcUsername = "johndoe"

# RPC password of a Corda node to connect to. Should be specified only if corda-rpc or corda-auto transport is used.
rpcPassword = "10004"

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

# corda-updates-shell

`corda-updates-shell` provides a Command Line Interface for CorDapp Distribution Service. The CLI is build using the [CordaCLIWrapper](https://docs.corda.net/head/cli-ux-guidelines.html) and supports all of its features.

## Usage

`corda-updates-shell` supports the following modes:

* **INIT**. Initialises an empty local repository and creates a sample configuration file.
```bash
# Will initialize an empty local repository and create a settings.conf file under USER.HOME/.corda-updates folder
java -jar corda-updates-shell-xxx.jar --mode=INIT

# Will initialize an empty local repository and create a settings.conf file under ~/.my-repo
java -jar corda-updates-shell-xxx.jar --mode=INIT --configPath="~/.my-repo"

```  
* **SYNC**. Synchronises the contents of the local repository with the remote repositories, configured in the `settings.conf`. All versions that are missing in the local repository will be downloaded during the synchronisation. 
```bash
# Will pull down locally missing versions of all CorDapps that are configured in the settings.conf file. 
java -jar corda-updates-shell-xxx.jar --mode=SYNC

# Will pull down locally missing versions of "net.corda:corda-finance" CorDapp starting from the version 0 and up to the version 2.0 not inclusively.
java -jar corda-updates-shell-xxx.jar --mode=SYNC --cordapp="net.corda:corda-finance:[,2.0)"

```  
* **PRINT_VERSIONS**. Prints available versions of the specified CorDapp to the screen.
```bash
# Will print all available versions of "net.corda:corda-finance" CorDapp. 
java -jar corda-updates-shell-xxx.jar --mode=PRINT_VERSIONS --cordapp="net.corda:corda-finance:[,)"
```

Configuration file is looked up in the following order:
* A custom path if one was provided via `--configPath` parameter 
* `settings.conf` in the current working folder
* `settings.conf` in `USER.HOME/.corda-updates`

## How to use the shell

1. Download `corda-updates-shell` jar or build it by yourself. TODO: download link here
2. Initialise a local repository via `java -jar corda-updates-shell-xxx.jar --mode=INIT`
3. Add CorDapps that you would like to watch to the `settings.conf`
4. Start using the utility by invoking `SYNC` or `PRINT_VERSIONS` commands

# corda-updates-app

`corda-updates-app`is a CorDapp that provides the following functionality:
* A scheduled state to periodically check for new updates availability
* Flows to schedule updates and to retrieve available CorDapp versions
* Flows to report information about CorDapp versions to the BNO

## Scheduling synchronisation

Updates can be scheduled via `ScheduleSyncFlow`. Synchronisation interval is driven by `syncInterval` configuration property and defaults to *once in 5 hours*. `ScheduleSyncFlow` can be started from the shell or via RPC. 

```kotlin
// synchronisation should be launched asynchronously if corda-flows transport is used 
subFlow(ScheduleSyncFlow(launchAsync = true))
```

> If Corda-based transports are not used, then ScheduleSyncFlow can be run in synchronous mode (for example if all remote repositories are configured to use -http or -file transports). Synchronous invocations are more convenient from a developer's perspective as results are available straight after the execution is finished. However, launching ScheduleSyncFlow in synchronous mode for Corda-based transports might result to a deadlock on a single-threaded flavours of Corda. Please refer to "Asynchronous invocations" section for more information about that.

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

Configuration is loaded from `cordapps/config/corda-updates-app.conf` file in the node's folder. Repository hoster node is required only if `corda-flows` transport is used.
```
# URL of the repository where the hoster would serve the artifacts from. Supports http(s) and file transports. Proxies and authentication are not supported 
remoteRepoUrl=file:/path/to/local/repository

# Class that implements the SessionFilter interface. Optional.
sessionFilter=com.my.app.MySessionFilter
```

## Reporting CorDapp versions

Versions of installed CorDapps can be reported via `ReportCordappVersionFlow`. CorDapp Distribution Service doesn't collect information about installed CorDapps by itself. Instead, CorDapps are expected to report their versions by themselves. Version reporting can help BNOs to identify if any participants have an outdated versions of CorDapps installed and to contact them offline to ask to upgrade. 

BNOs can use `GetCordappVersionsFlow` and `GetCordappVersionsForPartyFlow` to query reported versions on their side.