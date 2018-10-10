CorDapp Distribution Service
============================

CorDapp Distribution Service allows Corda network operators to distribute CorDapps updates to their network members. Please see the [design doc](./design/design.md) for more information on the technical implementation.

CorDapp Distribution Service utilises Maven repositories for artifact distribution (as CorDapps are effectively fat-jars). Corda network / business network operators can run Maven on their premises and distribute CorDapps over conventional HTTP(s) or bespoke Corda transports (which are described below). 

*CorDapp Distribution Service* consists of the following components:
* **corda-updates-core** - core classes that provide a unified API over Maven Resolver
* **corda-updates-transport** - custom transport implementations for Maven Resolver over Corda Flows
* **corda-updates-app** - CorDapp that provides Corda API over `corda-updates-core`, allows network participants to periodically check for updates and enables basic version-reporting functionality.
* **corda-updates-shell** - command line interface over `corda-updates-core`

# corda-updates-core

`corda-updates-core` sits in the heart of CorDapp Distribution Service and provides generic APIs to download CorDapps from remote Maven repositories over `file`, `http(s)`, `corda-flows` and `corda-rpc` transports. `corda-updates-core` is used internally only and is not visible to the end users. It effectively allows to:
* Get a list of artifact versions available in a remote repository
* Download a single version of an artifact from a remote repository
* Download a version range from a remote repository, which is effectively combination of the two previous steps.
* Associate different CorDapps with different remote repositories and to sync them down via a single method invocation. 

`corda-updates-core` also supports basic authentication for HTTP proxies and remote repositories. 

# corda-updates-transport

`corda-updates-transport` provides a bespoke Maven Resolver transport implementations over Corda Flows and Corda RPC.

## Why do we need it?

Corda-based transports would allow repository hosters to enforce their custom rules onto incoming requests and to filter out any unauthorised download attempts. For example CorDapp Distribution Service can be easily integrated with [Business Networks Membership Service](https://github.com/corda/corda-solutions/tree/master/bn-apps/memberships-management), that would effectively allow it to filter out any non Business Network traffic. 

## Session Filters

Corda-based transports allow developers to implement their custom `SessionFilter`s, which can be used to allow / disallow incoming download requests. `SessionFilter` is a simple interface which, if implemented, is invoked against every incoming download request. `SessionFilter` returns a boolean that indicates whether to request should be let through or not.

```kotlin
interface SessionFilter {
    @Suspendable
    fun isSessionAllowed(session : FlowSession, flowLogic : FlowLogic<*>) : Boolean
}
```
For example a session filter that would allow only Business Network traffic in can be implemented in the following way:

```kotlin
class BusinessNetworkSessionFilter : SessionFilter {
    @Suspendable
    override fun isSessionAllowed(session : FlowSession, flowLogic : FlowLogic<*>) = flowLogic.subFlow(GetMembershipsFlow())[session.counterparty] != null
}
``` 

## Transport modes

The following transports are supported:
* **corda-flows** allows to transfer data over Corda Flows. It can be used only when CorDapp Distribution Service is invoked from *inside a Corda node*.
* **corda-rpc** allows to transfer data over Corda RPC. It reuses the same flows as `corda-flows` transport, but invokes them via RPC instead. `corda-rpc` can be used when CorDapp Distribution Service is invoked from *outside of a Corda node*, i.e. from a shell or a third-party application.
* **corda-auto** is an automatic switch between `corda-rpc` and `corda-flows` transports. The underlying transport is chosen based on the value of `corda-updates.mode` custom session property, that can be set in the realtime based on the invocation context. The main purpose for this mode - is to allow Cordapp Distribution Service to reuse the same configuration file, regardless of where the transport was invoked from inside or outside of Corda. 

Corda-based transports expect a remote repository URL to be specified in the format of `transport-name:x500Name`. For example `corda-auto:O=BNO,L=New York,C=US` (just imagine that you specify Corda X500 name instead of HTTP host). 

# Configuration

CorDapp distribution service is configurable from a `yaml` file, of the following structure

```yaml
#Path to the local repository
localRepoPath: ~/.corda-updates/repo

# HTTP or HTTPS. Should be specified only if HTTP(S) proxy is used.
httpProxyType: HTTP

# Proxy host name. Should be specified only if HTTP(S) proxy is used.
httpProxyHost: 10.0.0.1

# Proxy port. Should be specified only if HTTP(S) proxy is used.
httpProxyPort: 10009

# Username for proxy authentication. Should be specified only if HTTP(S) proxy is used.
httpProxyUsername: proxy_user

# Password for proxy authentication. Should be specified only if HTTP(S) proxy is used.
httpProxyPassword: P@$$w0rD

# RPC host of a Corda node to connect to. Should be specified only if corda-rpc or corda-auto transport is used.
rpcHost: localhost

# RPC port of a Corda node to connect to. Should be specified only if corda-rpc or corda-auto transport is used.
rpcPort: 8003

# RPC username of a Corda node to connect to. Should be specified only if corda-rpc or corda-auto transport is used.
rpcUsername: johndoe

# RPC password of a Corda node to connect to. Should be specified only if corda-rpc or corda-auto transport is used.
rpcPassword: 10004

# List of CorDapps to sync from remote repos. Can sync multiple CorDapps from multiple repositories
cordappSources:
- remoteRepoUrl: https://repo.maven.apache.org/maven2/

  # list of CorDapps to sync from this repo. Can be many. Should be specified in the form of "artifactGroup:artifactName"
  cordapps:
  - net.corda:corda-finance

  # Username, if the remote repo uses basic HTTP authentication
  httpUsername: repo_user

  # Password, if the remote repo uses basic HTTP authentication
  httpPassword: r3p0_P@$$

```

Configuration file is looked up in the following order:
* A custom path if one was provided by the calling site 
* `settings.yaml` in the current working folder
* `settings.yaml` in `USER.HOME/.corda-updates`

Both `corda-updates-shell` and `corda-updates-app` support custom configuration path overrides, which are described below.

# corda-updates-shell

`corda-updates-shell` provides a Command Line Interface for CorDapp Distribution Service. The CLI is build with [CordaCLIWrapper](https://docs.corda.net/head/cli-ux-guidelines.html) and supports all of its features.

## Usage

`corda-updates-shell` supports the following modes:

* **INIT**. Initialises an empty local repository and creates a sample configuration file.
```bash
# Will initialize an empty repository and create a settings.yaml file under USER.HOME/.corda-updates folder
java -jar corda-updates-shell-xxx.jar --mode=INIT

# Will initialize an empty repository and create a settings.yaml file under ~/.my-repo
java -jar corda-updates-shell-xxx.jar --mode=INIT --configPath="~/.my-repo"

```  
* **SYNC**. Synchronises the contents of the local repository with the remote repositories, configured in the `settings.yaml`. All versions missing from the local repository will be downloaded during the synchronisation. 
```bash
# Will pull down locally missing versions for all CorDapps configured in the settings.yaml file. 
java -jar corda-updates-shell-xxx.jar --mode=SYNC

# Will pull down locally missing versions of "net.corda:corda-finance" CorDapp starting from the version 0 and up to the version 2.0 not inclusively.
java -jar corda-updates-shell-xxx.jar --mode=SYNC --cordapp="net.corda:corda-finance:[,2.0)"

```  
* **PRINT_VERSIONS**. Prints available versions of the specified CorDapp to the screen.
```bash
# Will print all available versions of "net.corda:corda-finance" CorDapp. 
java -jar corda-updates-shell-xxx.jar --mode=PRINT_VERSIONS --cordapp="net.corda:corda-finance:[,)"
```

> All `corda-updates-shell` commands support a configuration path override via `--configPath` parameter. For example `java -jar corda-updates-shell-xxx.jar --mode=SYNC --configPath="~/my-repo/settings.yaml"`

## How to use

1. Download `corda-updates-shell` jar or build it by yourself. TODO: download link here
2. Initialise a local repository via `java -jar corda-updates-shell-xxx.jar --mode=INIT`
3. Add CorDapps that you would like to watch to `settings.yaml`
4. Start using the utility by invoking `SYNC` or `PRINT_VERSIONS` commands

# corda-updates-app

`corda-updates-app`is a CorDapp that provides the following functionality:
* A scheduled state to periodically check for new updates
* Corda APIs over CorDapp Distribution Service functionality
* Flows to report installed information about installed CorDapps to the BNO

## Asynchronous mode

`cora-updates-app` flows should be started in *asynchronous* mode when `corda-flows` or `corda-auto` transport is used. This is because `corda-flows` transport starts a separate flow (in a separate thread) for data transfer to prevent Maven Resolver internals from getting checkpointed (as they are not `@Suspendable`). As Corda OS flows engine is single-threaded, invoking any of Maven Resolver methods synchrnously would result into a deadlock.

All flows that use Maven Resolver under the hood support `laynchAsync` flag which is `true` by default. When a flow is started in async mode, it would use a separate thread pool to trigger Maven Resolver operation and would return immediately without waiting for the operation to complete. Results of operation invocation would be published to `ArtifactsMetadataCache` once the invocation is finished.

The following code snippet shows how to get the results

```kotlin
class MyFlow : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val artifactsCache = serviceHub.cordaService(ArtifactsMetadataCache::class)
        asrtifactCache.cache // <- results will appear populated here
    }
}
```

The best way to use the CorDapp Distribution Service - is to schedule periodic updates via `ScheduleSyncFlow` (described below). Then contents of `ArtifactsMetadataCache` will always be up to date.

Asynchronous mode doesn't have to be used when running on Corda Enterprise (CE) as CE's flows engine is multithreaded.    

## Scheduling synchronisation

Updates can be scheduled via `ScheduleSyncFlow`. Synchronisation interval is driven by `syncInterval` configuration property and defaults to *5 hours*. `ScheduleSyncFlow` can be started from shell or RPC. 

```kotlin
// synchronisation should be launched asynchronously if corda-flows transport is used 
subFlow(ScheduleSyncFlow(launchAsync = true))
```
## Getting available versions

Available versions can be fetched via `GetAvailableVersionsFlow`. 

```kotlin
subFLow(GetAvailableVersionsFlow("net.corda:corda-finance"))
```

> Available versions are taken from the `ArtifactsMetadataCache` and hence can be fetched synchronously. At least on synchronisation has to be done for the contents of the cache to be populated. 

## Configuration

`corda-updates-app`loads its configuration file from `cordapps/config/corda-updates-app.conf` file. Configuration is different for participants and repository hosters.  

Participant configuration:
```
# path to the yaml configuration file (described in the previous sections) 
configPath=./config.yaml

# synchronisation interval in milliseconds
syncInterval=9999999

# identity of the notary to use
notary="O=Notary,L=London,C=GB"

# identity of the BNO to report CorDapp versions to 
bno="O=BNO,L=London,C=GB"
```

Repository hoster configuration (required only if `corda-flows` transport is used):
```
# URL of the repository where the hoster would serve the artifacts from. Supports http(s) and file transports. Proxies and authentication is not supported 
remoteRepoUrl=file:/path/to/local/repository
```
## Stopping CorDapp from working if a newer version is available

Such rules can be enforced in the following way:

```kotlin
class MyFlow : FlowLogic<Unit>() {
    companion object {
    // current version of CordDapp. Can be hardcoded as a constant or get received from serviceHub starting from Corda v4
        val CURRENT_VERSION = "1.0"
    }

    @Suspendable
    override fun call() {
        val artifactMetadata = subFlow(GetAvailableVersionsFlow("my.cordapp.group:my-cordapp-id"))
        if (artifactMetadata != null && artifactMetadata.versions.isNotEmpty()) {
            // versions are sorted in ascending order
            if (CURRENT_VERSION != artifactMetadata.versions.last()) {
                throw FlowException("Please update your CorDapp") 
            }
        }
        
        // .....
    }
}
```

## How to use

1. Download `corda-updates-shell` jar or build it by yourself. TODO: download link here
2. Initialise a local repository via `java -jar corda-updates-shell-xxx.jar --mode=INIT`
3. Add CorDapps that you would like to watch to `settings.yaml`
4. Download `corda-updates-app` jar or build it by yourself. TODO: download link here
5. Install the CorDapp to participant's nodes. Configure the CorDapp with `corda-updates-app.conf` (as described in the previous sections) and point it to the `settings.yaml` file created at the step #2.
6. If Corda-based transports are used then install the CorDapp to the repository hoster's node and point it to Maven repo via `corda-updates-app.conf` as described in the previous sections.   
7. Schedule periodic sync by invoking `ScheduleSyncFlow` from Corda shell
8. Updates will periodically be downloaded to the local repository.