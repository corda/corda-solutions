corda-updates-shell
==================================

This repository contains an implementation of a Command Line Interface for `corda-updates` utility. The CLI is build with [CordaCLIWrapper](https://docs.corda.net/head/cli-ux-guidelines.html) and supports all of its features.

TODO: Add references about transports and etc.

# Usage

*corda-updates-shell* supports in 3 different modes:

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

# Configuration

*corda-updates-shell* reads its configuration from a yaml file. 

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


## Configuration resolution

Configuration file is looked up in the following order: 
1. An explicit path if one has been provided via command line `--configPath` parameter
2. `settings.yaml` in the current working folder
3. `settings.yaml` in `USER.HOME/.corda-updates`


# How to start

1. Download *corda-updates-shell* jar or build it by yourself
2. Initialise a local repository via `java -jar corda-updates-shell-xxx.jar --mode=INIT`
3. Add CorDapps that you would like to sync to `settings.yaml`
4. Start using the utility by invoking `SYNC` or `PRINT_VERSIONS` commands

 
  
