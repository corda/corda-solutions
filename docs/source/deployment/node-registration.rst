Node Registration
=================


On the Corda Node change directory into the node directory:

1. On the Corda Node change directory into the node directory:

corda@myserver:~$ cd /opt/corda

2. Start the Corda Node by specifying Proxy Server and Proxy Server Port

corda@myserver:/opt/corda$ java -Dhttps.proxyHost=PROXYSERVER -Dhttps.proxyPort=8080 -jar corda.jar. --initial-registration --network-root-truststore-password trustpass

3. If successful you will see the following output in your terminal:


.. image:: nodereg.png
   :scale: 50%
   :align: left


4. Corda Operations Report will receive a CSR JIRA ticket acknowledging that a registration request has been made. The Operations team will seek to identify which legal entity is making the request prior to approval. 

5. The Corda JVM process will be suspended until R3 Operations approve the CSR and the node  has received the certificate from the Doorman.

6. Once received, your node will acknowledge receipt and shut down. 

7. You can then restart your Corda Node as follows:

corda@myserver:/opt/corda$ java -Dhttps.proxyHost=PROXYSERVER -Dhttps.proxyPort=8080 -jar corda.jar and you will see output like this:




