corda-updates-core
==================================

`corda-updates-core` allows to download CorDapps from remote Maven repositories via file, http(s), corda-flows and corda-rpc transports.

`corda-updates-core` consists of 2 main classes: `CordaMavenResolver` and `CordappSyncer`

# CordaMavenResolver

`CordaMavenResolver` is a wrapper around [Maven Resolver](https://maven.apache.org/resolver/index.html) library and allows to:
* Download a single version of an artifact from a remote repository
* Get a list of an artifact versions available in a remote repository
* Download a version range of an artifact from a remote repository, which is effectively the combination of the previous two.

`CordaMavenResolver` supports bespoke transports over Corda flows and Corda RPC and can be effectively integrated into Corda ecosystem. 

# CordappSyncer

`CordappSyncer` is an abstraction over `CordaMavenResolver` that allows to associate different CorDapps with different remote repositories and to sync them down via a single method invocation.

`CordappSyncer` is configurable via `SyncerConfiguration`, which is usually red from a yaml file. 
