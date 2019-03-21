Node Registration
=================

Overview
~~~~~~~~
The diagram above illustrates the process of joining the Corda Network in UAT & PROD. The first few steps may happen any time before the actual Corda Node registration in order to retrieve the public key. All new joiners to the network must supply their node host public IP address to be whitelisted against the UAT 3.2 Network Map.

Whitelist IP & Trust Cert

1. Send email to doorman@r3.com using email address representing the enterprise whose Legal Identity will be registered, providing IP address of machine that will listen for traffic (Float).
#. R3 will whitelist your IP address.
#. You will receive a network-root-truststore.jks file containing the public key certificate from the Corda compatibility zone to which you are registering, e.g., Corda Network UAT or Corda Network. This is the file you will use later in the process to register your Corda Node once it is configured.
#. The network-root-truststore.jks file is required to setup SSL connections with the Doorman. The network-root-truststore.jks file contains the Doorman's public certificates. This should go in the /certificates folder in your Corda directory.
#. Configure your node.conf with the required entries to join the Corda UAT Network.
#. For each node run the initial registration process. The Corda Node will send a CSR request to the Doorman and when it is accepted the Node will begin polling Doorman until it's certificate is available for download.
#. The Node will download and install the certificate and shut down.
#. Upon restarting the Node after initial registration the node will create and send its Network Map entry (NodeInfo-*) and also download all of the evailable entries for other Corda Nodes in the Network Map. The NodeInfo-* file is a data structure which contains IP Address, Identity and Platform Version.
#. The Network Map service will validate the Node NodeInfo-* and add it to the Network Map, and the Network Map will then be downloaded by other Nodes on the network within approximately 10 mins.
#. Once this process is complete your Corda Node can now transact with others in the Corda Network.



.. image:: ./resources/registration.png
   :scale: 100%
   :align: center

Actions
~~~~~~~

On the Corda Node change directory into the node directory:

1. On the Corda Node change directory into the node directory:

corda@myserver:~$ cd /opt/corda

2. Start the Corda Node by specifying Proxy Server and Proxy Server Port

corda@myserver:/opt/corda$ java -Dhttps.proxyHost=PROXYSERVER -Dhttps.proxyPort=8080 -jar corda.jar. --initial-registration --network-root-truststore-password trustpass

3. If successful you will see the following output in your terminal:


.. image:: ./resources/nodereg.png
   :scale: 50%
   :align: left


4. Corda Operations Report will receive a CSR JIRA ticket acknowledging that a registration request has been made. The Operations team will seek to identify which legal entity is making the request prior to approval.

5. The Corda JVM process will be suspended until R3 Operations approve the CSR and the node  has received the certificate from the Doorman.

6. Once received, your node will acknowledge receipt and shut down.

7. You can then restart your Corda Node as follows:

corda@myserver:/opt/corda$ java -Dhttps.proxyHost=PROXYSERVER -Dhttps.proxyPort=8080 -jar corda.jar and you will see output like this:


.. literalinclude:: ./resources/nodestart.conf
    :language: javascript
