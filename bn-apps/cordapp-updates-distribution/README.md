CorDapp Distribution Service
============================

CorDapp Distribution Service allows Corda network operators to distribute updates to their CorDapps to the network participants. Please see the [design doc](./design/design.md) for more information on the technical implementation.

CorDapp Distribution Service utilises Maven repositories for artifact distribution (as CorDapps are effectively fat-jars). Corda network operator can host a Maven repo on their premises, where the network members can download artifacts from.

Reusing Maven repositories would allow network operators to easily integrate CorDapp Distribution Service into their CI/CD pipelines.

*CorDapp Distribution Service* consists of the following components:
* **corda-updates-core** - core classes that provide a unified API over Maven Resolver
* **corda-updates-transport** - custom transport implementations for Maven Resolver over Corda Flows
* **corda-updates-app** - CorDapp that provides Corda API over `corda-updates-core`, allows network participants to periodically check for updates and enables basic version-reporting functionality.
* **corda-updates-shell** - command line interface over `corda-updates-core`

# corda-updates-core

`corda-updates-core` sits in the heart of CorDapp Distribution Service and provides the functionality to download CorDapps from remote Maven repositories via `file`, `http(s)`, `corda-flows` and `corda-rpc` transports.

It effectively consists of two main classes: 
* `CordaMavenResolver` - a wrapper around [Maven Resolver](https://maven.apache.org/resolver/index.html) library that allows to:
    * Get a list of artifact versions available in a remote repository
    * Download a single version of an artifact from a remote repository
    * Download a version range from a remote repository, which is effectively the combination of the two previous steps.
* `CordappSyncer` - an abstraction over `CordaMavenResolver` that allows to associate different CorDapps with different remote repositories and to sync them down via a single method invocation. It is configurable via `SyncerConfiguration`, which is usually red from a yaml file.

`corda-updates-core` is used by CorDapp Distribution Service internals and is transparent to the end user.

# corda-updates-transport

`corda-updates-transport` provides a bespoke Maven Resolver transport implementations over Corda Flows and Corda RPC.

## Why do we need it?

Corda-based transports would allow repository hosters to enforce their custom rules onto incoming requests and to filter out any unauthorised download attempts. For example CorDapp Distribution Service can be integrated with [Business Networks Membership Service](https://github.com/corda/corda-solutions/tree/master/bn-apps/memberships-management), that would effectively allow it to filter out all non Business Network traffic. 

## Transport modes

The following transports are supported:
* **corda-flows** allows to transfer data over Corda Flows. It is used only when the transport is invoked from inside a Corda node.
* **corda-rpc** allows to transfer data over Corda RPC. It reuses the same flows as `corda-flows` transport, but invokes them via RPC instead. `corda-rpc` is used when the transport is invoked from the outside of a Corda node, i.e. from a shell or a third-party application.
* **corda-auto** is an automatic switch between `corda-rpc` and `corda-flows` transports. The underlying transport is chosen based on the value of `corda-updates.mode` custom session property, that can be set in realtime based on the invocation context. The main purpose for this mode - is to allow Cordapp Distribution Service to reuse the same configuration file, regardless of where the transport was invoked from inside or outside of Corda. 

Corda-based transports expect a repository URL to be specified in the format of `transport-name:x500Name`, i.e. for example `corda-auto:O=BNO,L=New York,C=US` (just imagine that instead of http host you specify Corda X500 name). 

## Session Filters

Corda-based transports allow developers to implement their custom *session filters* to filter out any unauthorised download attempts. Session filters are invoked for every download attempt and are represented with `SessionFilter` interface.

# corda-updates-shell

`corda-updates-shell` provides a CLI for CorDapp Distribution Service. The CLI is build with [CordaCLIWrapper](https://docs.corda.net/head/cli-ux-guidelines.html) and supports all of its features.

## Usage

`corda-updates-shell` supports the following modes:

* **INIT**. Initialises an empty local repository and creates a sample configuration file.
```bash
# Will initialize an empty repository and create settings.yaml under USER.HOME/.corda-updates folder
java -jar corda-updates-shell-xxx.jar --mode=INIT

# Will initialize an empty repository and create settings.yaml under the specified path
java -jar corda-updates-shell-xxx.jar --mode=INIT --configPath="path_to_some_folder"

```  
* **SYNC**. Synchronises the contents of the local repository with the remote repositories, configured in `settings.yaml`. All versions missing from the local repository will be downloaded during the synchronisation.
```bash
# Will pull down locally missing versions for all CorDapps configured in settings.yaml file. 
java -jar corda-updates-shell-xxx.jar --mode=SYNC

# Will pull down locally missing versions of "net.corda:corda-finance" CorDapp starting from the version 0 and up to the version 2.0 not inclusively.
java -jar corda-updates-shell-xxx.jar --mode=SYNC --cordapp="net.corda:corda-finance:[,2.0)"

```  
* **PRINT_VERSIONS**. Prints available versions of the specified CorDapp to the screen.
```bash
# Will print all available versions of "net.corda:corda-finance" cordapp. 
java -jar corda-updates-shell-xxx.jar --mode=PRINT_VERSIONS --cordapp="net.corda:corda-finance:[,)"
```

`corda-updates-shell` allows to explicitly specif which configuration file to used via `--configPath` parameter.

## How to use

1. Download *corda-updates-shell* jar or build it by yourself
2. Initialise a local repository via `java -jar corda-updates-shell-xxx.jar --mode=INIT`
3. Add CorDapps that you would like to sync to `settings.yaml` (please see Configuration section below)
4. Start using the utility by invoking `SYNC` or `PRINT_VERSIONS` commands

# Configuration

CorDapp Distribution Service reads its configuration from a yaml file. Configuration file is looked up in the following order: 
* `settings.yaml` in the current working folder
* `settings.yaml` in `USER.HOME/.corda-updates`

Configuration location overrides are supported by both `corda-updates-shell` and `corda-updates-app`.

## YAML structure

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