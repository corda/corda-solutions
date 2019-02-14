============================
Command Line Interface (CLI)
============================

The API style follows that of Docker, some key points being:

- commands that create resources return the ID of the resource created

- an explicit named param for each option

- sensible defaults for options, so that the commands can be quite succinct in many cases

- when specifying resources, the commands accept either a user allocated name or the internal id

- any complex data is returned as a JSON string


To get an idea lets walk through some simple examples.

Listing and creating networks
-----------------------------

::

  cctl network ls
  NETWORK               STATUS            SELECTED           NETWORK_ID
  testnet               available                            f45a17deeb0d761a
  uat                   available                            787a17677612ba51
  tcn                   unavailable                          541ba5d1e3476a89

So this just lists the possible networks. In this case there are the three default one provided by the Corda
Foundation.

::

  cctl local-network standup --name demo
  65ad3198a712bc34

We decided that we really needed a self contained local network - this is typically for local development or
isolated testing. The ID of the new network has been returned.

::

  cctl network ls
  NETWORK               STATUS            SELECTED           NETWORK_ID
  demo                  available         yes                65ad3198a712bc34
  testnet               available                            f45a17deeb0d761a
  uat                   available                            787a17677612ba51
  tcn                   unavailable                          541ba5d1e3476a89

The new network is ready for use and selected by default. We can get more info using the ``inspect`` command.

::

  $cctl network inspect
  {
     "id" : "65ad3198a712bc34",
     "name" : "demo",
     "notaries" : [{ "O=Notary,L=London,C=GB" }]
  }


Adding nodes and deploying apps
-------------------------------

This is a more complete example

::

  $cctl provisioner select local
  $cctl local-network standup
  $cctl distributor add --file /path/to/myapp.jar
  $cctl node create [Alice,Bob,Charlie]


Notes
-----

``cctl provisioner select local`` picks the agent for provisioning to a local machine (so it can simply
 execute normal shell commands).

``cctl local-network standup``  will automatically create a new local-network and select it for use. As no other option
is given the minimal viable network is assumed, which has a single notary.

``cctl distributor add --file /path/to/myapp.jar`` has registered this app ready for
distribution, but there aren't any nodes. We could create the nodes before adding our app to the distributor, but
this would probably run more slowly, most provisioners would start the node and then need to restart it to pick up
the new app.

``cctl node create [Alice,Bob,Charlie]`` will create the nodes, at which point they pickup the
app registered with the provisioner. A full X500 name is generated from the simple names using the rules in the
provisioner.

