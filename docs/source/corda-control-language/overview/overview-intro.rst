============
Intro to CCL
============

Currently there is no standard way in which to describe how Corda Nodes are provisioned, how they are
assembled into useful `Business Networks`_ with other service such as Notaries,
and how to to manage these networks, as an example automating where possible the distribution of
updates to CorDapps whilst ensuring that other policies like Node availability are enforced.

This has resulted in a confusing mixture of partial technical solutions, each at differing levels of maturity
and abstraction. Developers typically use Gradle tasks like 'deployNodes', while DevOps build scripts
in tools like Ansible and Kubernetes. And when we move to cloud platforms there is the added complexity
that each has their own native API and toolset.

Stepping back from the problem, it is easy to see why this has happened. We need the flexibility to use this variety of tools, but
each is tuned to fit a different problem (Gradle for developers, Ansible for general automation, Kubernetes for building
and managing at cloud scale and so on). So they have very opinionated designs, and none will on its own cover the full problem
domain.

Corda Control Language take some of the ideas of Docker and the "Infrastructure as Code" movement to define standard
notations to describe and run Corda Nodes. This language has three parts that build on layer below.

- An "Infrastructure as Code" model to define how a Node is built. This describes concepts like firewalls, load balancer, database setup, allocation of memory, CPU and disk. In Docker terms this is roughly analogous to the Dockerfle used to define images

- A CLI tool for creating and administrating nodes based on these definitions. This is similar to the Docker CLI.

- An orchestration language for defining and managing working Networks. In Docker terms this is similar to Docker Compose and Docker Swarm.


Of course on its own this is only partly useful, so the intention is to also ship some pre built 'provisioners' that
will run against some commonly used tooling such as Docker. It is hoped that the wider Corda community will
support implementations for other tools.



.. _Business Networks: ../../business-networks/intro.html

.. toctree::
   :maxdepth: 1

   command-line-interface.rst
   modelling-networks.rst
   modelling-nodes.rst