=========================
cctl distributor
=========================

*Work in progress*

The distributor encapsulates the rules for pushing out new or updated apps
 to a set of nodes. It is expected that by selecting a provisioner
 we automatically get its associated default distributor.

There are three basic behaviours to define.

- how apps are physically distributed, this could include local filesystem, good old FTP and our maven based approach

- the set of nodes to distribute to. This is probably just defined by a basic hierarchy

  - an explicit list of nodes, if provided

  - all "transacting" nodes in the  network, if one is set up

- the policies for taking updates, eg.

  - auto apply and restart (the default probably in dev and test)
  - grace period before automatically applying the update (say 1 week)


Some example commands below

::

  $ cctl distributor ls

  DISTRIBUTOR           STATUS            SELECTED           DISTRIBUTOR_ID
  local                 active                               8d99db4c4e5a64de
  maven                 disable                              d9757deeb0db0d2e

  cctl distributor select local
  8d99db4c4e5a64de

  $ cctl distributor inspect
  {
    "dropFolder" : "/users/foo/corda/distibutor",
    "policy" : "auto"
  }

  $ cctl distributor distibute --file /path/to/myapp.jar
  e3237dae23db0d4d

  $ cctl distributor jobs ls

  JOB_ID                STATUS            APPS
  e3237dae23db0d4d      running           myapp.jar

  $ cctl distributor jobs e3237dae23db0d4d inspect
  {
      "nodes" : {
         "alice" : { "status" : "InProgess" },
         "bob" : { "status" : "Completed" },
         "charlie" : { "status" : "Failed", "message" : "Node offline" }
     }
  }



