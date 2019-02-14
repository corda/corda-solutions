=========================
cctl provider
=========================

*Work in progress*


For now an example of basic command is documented below.


::

  $ cctl provider ls

  PROVISIONER           STATUS            SELECTED           PROVIDER_ID
  local                 running                              8d99db4c4e5a64de
  azure-simple          stopped                              d9757deeb0db0d2e

  $ cctl provider select azure-simple
  8d99db4c4e5a64de

