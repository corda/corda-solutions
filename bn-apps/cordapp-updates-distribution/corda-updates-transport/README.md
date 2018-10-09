corda-updates-transport
==================================

This repository contains the implementation of Maven Resolver over Corda flows and Corda RPC

# Why to use transports over Corda flows

Transports over Corda flows would allow repository hosters to enforce custom rules onto incoming requests and to filter out any unauthorised download attempts. For example `corda-updates` can be integrated with [Business Networks Membership Service](https://github.com/corda/corda-solutions/tree/master/bn-apps/memberships-management), that would effectively allow it to filter out all non Business Network traffic.

Such rules can be enforced via custom `SessionFilter`s implementations. 

# How to use transports over Corda flows

`corda-updates-transport` supports the following transport modes:
* **corda-flows** allows to transfer data over Corda flows. It should be used only when the library is invoked from within a Corda node.
* **corda-rpc** allows to transfer data over Corda RPC. It reuses the same flows as `corda-flows` transport, but invokes them via RPC instead. `corda-rpc` should be used when the library s invoked from the outside of a Corda node, i.e. from a shell or a third-party application.
* **corda-auto** is an automatic switch between `corda-rpc` and `corda-flows` transports. The underlying transport is chosen based on the value of `corda-updates.mode` custom session property, that can be set in realtime based on the invocation context. The main purpose for this mode is to allow `corda-updates` to reuse the same configuration file, regardless of where the library from inside or outside of Corda. 

Corda-based transports expect a repository URL to be specified in the format of `transport-name:x500Name`, i.e. for example `corda-auto:O=BNO,L=New York,C=US` (just imagine that instead of http host you specify a X500 name). 