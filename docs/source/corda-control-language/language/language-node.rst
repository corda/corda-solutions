=========================
cctl node
=========================

*Work in progress*

At a minimum, to provision a node, we need to know the X500 name, and the corda version (which could just
 be a default for the provider). So the simple command is

::

  $ cctl node create --name '[O=Acme,L=London,C=GB]'
  38622e87b2b21166


And more advanced form might be

::

  $ cctl node create --identity '[O=Acme,L=London,C=GB]' --name Acme --memory 4GB --clustering HA
  38622e87b2b21166


This says

- give the node a simple alias that can be used in other commands (rather than the id or the full X500 name)

- the node is allocated an explicit amount of memory

- we want clustering using the HA pattern (assumption being that each 'pattern' we identify has a unique name)


Note that this is a high level abstraction that is hiding the physical orchestration, for example in the case of
Azure this might include:

- creating resource groups

- allocating billing groups

- allocating new VMs

- installing Corda on the VM

- creating keys in Azure Key Vault

- starting the key signing request with Corda net

To discover the running nodes.

::

  cctl node ls

  NODE_ID           IDENTITY                STATUS      NAME
  38622e87b2b21166  [O=Acme,L=London,C=GB]  signing     Acme

In this case the node is not yet active as there is a manual identity verification check in progress. Assuming
it gets approved the status will transition to running

To find out more about a node run the inspect command - this would essentially return the node config
in JSON format.

::

  cctl node 38622e87b2b21166 inpect
  {
    "p2pAddress" : "143.2.43.123:10005"
    "rpcSettings" : {
        "address": "192.1.1.34:10006"
   }
  }

To see installed apps

::

  cctl node 38622e87b2b21166 apps ls
  JARNAME           HASH                  UPDATE
  cash.jar          56132e87b2b211aa      2019-01-12:123455Z


