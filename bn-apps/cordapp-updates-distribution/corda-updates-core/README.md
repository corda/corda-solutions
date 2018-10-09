corda-updates-core
==================================

This module allows user to download CorDapps from remote Maven repositories via file, http(s), corda-flows and corda-rpc transports.

`corda-updates-core` effectively consists of 2 main classes: `CordaMavenResolver` and `CordappSyncer`

# CordaMavenResolver

`CordaMavenResolver` is a wrapper around [Maven Resolver](https://maven.apache.org/resolver/index.html) library and allows users to:
* Download a single version of an artifact from the remote repository
* Get a list of available versions of an artifact
* Download a version range of an artifact from a remote repository, which is effectively the combination of the previous two actions

`CordaMavenResolver` supports transports over Corda flows and Corda RPC, which allow to efficiently integrate the library into Corda ecosystem. 

# CordappSyncer

`CordappSyncer` is an abstraction on top of `CordaMavenResolver` that allows users to associate different CorDapps with different remote repositories and to sync them down via a single method invocation.

`CordappSyncer` is configurable via `SyncerConfiguration`, which is usually red from a yaml file. 
