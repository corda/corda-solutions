Considerations
~~~~~~~~~~~~~~

Deploying on-premises within a bank presents a number of specific security challenges There is typically a strongly-layered approach to defense. At a minimum there will be 3 separate zones:

- DMZ (directly addressable from the Internet)
- Application (not addressable from the Internet)
- Data (no access to the Internet).
- DLT/Blockchain platforms present a significant new deployment challenge because they exhibit behavior similar to a peer-to-peer network; they both initiate and terminate connections with external hosts on the same port. This type of behavior is new in many enterprise deployments. This document summarizes the thinking around deploying R3 Corda within a Bank environment and provides detailed deployment recommendations.

In order to mitigate the security challenges of banks that are mentioned above Corda Enterprise includes the following Firewall components:

- Node (Flow Worker)
- Bridge
- Float

Given that the instructions here are intended for deploying Corda Node in the Corda Network, please be aware if the Corda Network operating environments from which you will choose:

- UAT
- PROD

Following are the high level steps to deploying and on-boarding Corda Nodes to the Corda Network:

1. Request the latest versions of Corda & Corda Firewall Jar Files from R3.

#. Provision the Virtual Machines on which the Corda & Corda JVM's will run.

#. Provide the Public IP Address of the VM on which your Corda Node will reside.

#. Provide the Public IP Address of the VM on which the Corda Float will reside.

#. Request your DBA team to provide a database for the Corda Vault in one of the supported platforms.

#. Request your Network Engineers open connections thru the firewall to the following R3 Network components.

#. Request your Network Engineers provide a HTTP Proxy (connection to Doorman & Network Map) and a SOCKS Proxy for connection to peers on the Corda Network.

#. Complete the Corda UAT Network Agreement https://fs22.formsite.com/r3cev/CordaUATAgreement2019/index.html

Stakeholders
~~~~~~~~~~~~
It is important at the outset to understand the goal of your deployment, and take into consideration the internal infrastructure stakeholders for example:

- Systems Administration
- Security
- Network
- Enterprise Architecture
- In addition it is important to understand your deployment environment i.e. will it be On Premises or Cloud based? Corda is Cloud agnostic and can be deployed as required in Azure, AWS, Google and other market offerings.
