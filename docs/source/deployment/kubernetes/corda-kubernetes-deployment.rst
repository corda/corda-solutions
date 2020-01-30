Corda Kubernetes Deployment
===========================

Now that we have reviewed the architecture and the prerequisites we can start talking about the actual deployment scripts.

The scripts are located at: `Github: Corda Kubernetes Deployment <https://github.com/corda/corda-kubernetes-deployment>`_

The scripts come with usage instructions of their own, but it is worth explaining the steps in more details.

There are a few distinct steps to deploying a Corda Node that are always required.

* The first step is registration / certificates generation.
* Then moving on to deployment
* And finally starting everything up and testing

----

Registration / Certificate Generation
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

As discussed on the `Prerequisites <prerequisites.html>`_ page, we need to generate a PKI structure for the Corda Firewall to use.

The generated certificates from this process should be copied to the following folder: ``helm/files/certificates/firewall_tunnel``. 

The required files are: ``bridge.jks``, ``float.jks`` and ``trust.jks``.

Once this has been done we will also need to register the Corda Node itself to generate TLS certificates and signing keys, with the next step.

For further information on Node registration please see `Node registration <https://solutions.corda.net/deployment/onprem/node-registration.html>`_

Initial registration
--------------------

The initial registration of a Corda Node is a one-time step that issues a Certificate Signing Request (CSR) to the Identity Manager on the Corda Network and once approved returns with the capability to generate a full certificate chain which links the Corda Network Root CA to the Subordinate CA which in turn links to the Identity Manager CA and finally to the Node CA.
This Node CA is then capable of generating the necessary TLS certificate and signing keys for use in transactions on the network.

This process is generally initiated by executing ``java -jar corda.jar initial-registration``.
The process will always need access to the Corda Network root truststore. This is usually assigned to the above command with additional parameters ``--network-root-truststore-password $TRUSTSTORE_PASSWORD --network-root-truststore ./workspace/networkRootTrustStore.jks``.
The ``networkRootTrustStore.jks`` file should be placed in folder ``helm/files/network``.
Once initiated the Corda Node will start the CSR request and wait indefinitely until the CSR request returns or is cancelled.
If the CSR returns successfully, next the Node will generate the certificates in the folder ``certificates``.
The generated files from this folder should then be copied to the following folder: ``helm/files/certificates/node``.

----

CorDapps
~~~~~~~~

Any CorDapps that you would like to install on the Corda Node during the deployment should be placed in folder: ``helm/files/cordapps``.
All files in this folder can then be automatically found in the Corda Node after executing ``pre-install.sh`` in folder ``helm/output/corda/templates``.

----

Deployment
~~~~~~~~~~

At this point the one-time setup should be completed and we can move on to the actual deployment.
We'll start with executing Helm to compile the template into a set of output files that we can directly apply into the Kubernetes cluster to create resources.

Helm settings / configuration
-----------------------------

Helm works by taking values in a special file called ``values.yaml`` and applying those values to the files in folders ``files`` and ``templates``.
The ``values.yaml`` file is well documented on its own and should be filled in carefully and diligently. Do not make typos here, double-check every value.

Helm compile
------------

Now we are ready to compile the Helm templates into Kubernetes scripts.
Executing ``helm_compile.sh`` will do the following:

* Goes through the ``templates`` folder, for each file replaces variables by actual values from ``values.yaml`` file.
* Places the compiled templates into the ``output/templates`` folder.
* For the special file ``ConfigMap.yml``, combines the files in folder ``files`` into their raw text/binary representation and embeds them into ``ConfigMap.yml``. This file acts as way to inject files into Kubernetes pods.
* Lastly applies the generated Kubernetes files directly into the Kubernetes cluster using ``kubectl apply -f``.

Removing all installed resources from Kubernetes
------------------------------------------------

In the event you want a new fresh deployment of the Kubernetes cluster (especially useful during first few executions while learning the system), run the bash script called ``delete-all.sh``.
This file will delete Kubernetes cluster resources in the following order:

* PersistentVolumes
* PersistentVolumeClaims
* StatefulSets
* Deployments
* Services
* Pods
* Jobs

The script will then wait until all the resources have been deleted from the Kubernetes cluster by polling it every 5 seconds. Finally it will remove any PersistentVolumeClaims that have been released in the shutdown phase.
Once this script has finished executing, it is then safe to execute ``helm_compile.sh`` (or kubectl apply -f directly) to perform a fresh deployment.

----

Execution and testing
~~~~~~~~~~~~~~~~~~~~~

Execution
---------

Once the ``helm_compile.sh`` has deployed the resources to the Kubernetes cluster we are already in the startup sequence, where the pods will start and continue to self-organise and connect to the required components.
We just need to monitor and verify that everything gets initialised correctly. This is especially true if we have made a fresh deploy with values that have yet to be tested. There are many things that can be set incorrectly to provide a deployment that is not working, let's review some of these cases.

First thing first, get used to writing ``kubectl get pods``, this is your number one command for verifying if your components are running correctly.

.. code-block:: bash
 
    $ kubectl get pods
    NAME                                            READY   STATUS    RESTARTS   AGE
    corda-node-3-bridge-deployment-8d84c764-wx444   1/1     Running   0          28h
    corda-node-3-deployment-5f7cbf95bd-bznpv        1/1     Running   1          28h
    corda-node-3-float-deployment-6c74fd895-ngdvd   1/1     Running   0          28h

You should be looking at the **STATUS** column and verifying that it is listed as **Running**. If there are many **RESTARTS** listed for a given pod, it is likely to indicate an issue with the pod.

Next is to analyse the running pods, to see what the components inside them are doing.

We do this with the ``kubectl logs -f <pod>`` command, which directly shows the console log output for the given pod.
In the case of the Corda Node (using the above example it would look like this: ``kubectl logs -f corda-node-3-deployment-5f7cbf95bd-bznpv``). ::

    Node for "PartyE" started up and registered in 54.16 sec

This means that the Corda Node has successfully started and is running. Note, that this is not a guarantee that it can communicate successfully with the Corda Firewall let alone other nodes on the network.
We should perform the same kubectl logs command on the bridge and float in the above example to verify that they have both started successfully, but also that they have connected successfully to each other.

Lastly, we may need to go and inspect what is going on inside the pod.

We do this with the ``kubectl exec -it <pod> bash`` command. The command opens an interactive shell to the pod that we can use to analyse the running pod with.
On Windows, we may have to route the call via winpty helper to correctly route the interactive shell. ::

    winpty kubectl exec -it corda-node-3-deployment-5f7cbf95bd-bznpv bash

This should give us a bash command prompt to the running pod.
The default working folders for Corda Node and Corda Firewall is ``/opt/corda``.
We should perform normal Corda component investigation / trouble shooting from this point on.

----

Testing
-------

Connectivity
############

Now that we have the components up and running and have been able to verify that they connect to each other (at least according to the logs) we should run some further tests.
One such operation is verifying if our components can see the other components.
We can use ping / telnet to check if we can reach the other pods / ports.
There is a simple ``ping.sh`` script installed on the Corda Node in the workspace folder.
This script executes a simple check to see if a port is open:

.. code-block:: bash

    (echo > /dev/tcp/$IP/$PORT) > /dev/null 2>&1 && echo "UP" || echo "DOWN";

For testing Kubernetes services, you should enter the service name instead of an IP address, for example: ::

    (echo > /dev/tcp/corda-node-3-float-service/40000) > /dev/null 2>&1 && echo "UP" || echo "DOWN";
    UP

This indicates that the Corda Node can see and access the expected port on the Float service.
Should the command not return, it means that the port is open, but no process is responding on that port, which will eventually timeout and report DOWN.
This indicates you have an issue on the component in question.

Testing flows
#############

Once we have been able to verify that the deployment is connecting to the other components in the deployment correctly we can go ahead and see if we can communicate with the rest of the Corda Network we are connecting to.
The Helm chart has an option to enable sshd access to the Node, which will expose the port, and if you connect to that port with the RPC user with an ssh shell, you will get to the Corda Node shell.
In this shell, you can execute flows, just as if you were running an RPC client.
This makes it very easy to test if the Node sees the rest of the network and ultimately, if it can transact with other nodes on the network.
If we don't want to expose the ssh port to the rest of the network, we can also just expose it for the pod and connect to it with the following useful command: ::

    kubectl port-forward corda-node-3-deployment-5f7cbf95bd-bznpv 30000:30000
    Forwarding from 127.0.0.1:30000 -> 30000
    Forwarding from [::1]:30000 -> 30000

This allows us to connect from our local machine to the local IP address (of 127.0.0.1) and actually end up inside our Kubernetes cluster for the pod/port listed.
This is very useful indeed!

Let's start with testing if we can see the rest of the network. This is done by issuing a command to list the network map snapshot. ::

    run networkMapSnapshot
    addresses: "IP:60000"
    legalIdentitiesAndCerts: "O=PartyA1, L=London, C=GB"
    platformVersion: 4
    serial: 1570012229643

If we can see the other nodes on the network, it means we have connectivity to the Network Map server on the Corda Network.

Next we should check if we can perform a flow with another Node on the network. Preferrably a Node we know will respond to our request.
If for example we are running the Corda Finance flows, we should have two nodes running, where one will be the responder to the other nodes requests.
Corda Finance package has the capability to issue new Cash and transfer that Cash to another Node. ::

    flow start net.corda.finance.flows.CashIssueAndPaymentFlow

If after executing this flow successfully between two nodes, we are now live on the Corda Network with our Node which is running within a Kubernetes cluster!

Time to crack open a bottle of champagne!
