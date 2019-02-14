=========================
cctl network
=========================

*Work in progress*


Overview
--------

List, create and manage networks.

Some examples.

::

  $cctl network ls
  NETWORK               STATUS            SELECTED           NETWORK_ID
  testnet               available                            f45a17deeb0d761a
  uat                   available                            787a17677612ba51
  tcn                   unavailable                          541ba5d1e3476a89


  $cctl local-network standup --name demo
  65ad3198a712bc34

  $cctl network ls

  NETWORK               STATUS            SELECTED           NETWORK_ID
  demo                  available         yes                65ad3198a712bc34
  testnet               available                            f45a17deeb0d761a
  uat                   available                            787a17677612ba51
  tcn                   unavailable                          541ba5d1e3476a89

  $cctl network inspect
  {
     "id" : "65ad3198a712bc34",
     "name" : "demo",
     "notaries" : [{
         "O=Notary,L=London,C=GB"
     }]
  }
