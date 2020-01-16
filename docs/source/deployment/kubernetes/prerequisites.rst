Kubernetes Deployment option prerequisites
==========================================

.. line-block::
    The Kubernetes Deployment option has the following prerequisites:

    * A cloud environment with Kubernetes Cluster Services that has access to a Docker Container Registry
        (Note! The current version of the scripts only supports Azure out of the box by way of Azure Kubernetes Service and Azure Container Registry, future versions of the scripts will add support for other cloud providers)
    * Building the images requires local Docker installation `Docker <https://www.docker.com/>`_
    * kubectl is used to manage Kubernetes cluster `Install kubectl <https://kubernetes.io/docs/tasks/tools/install-kubectl/>`_
    * `Helm <https://helm.sh/>`_ 
        Helm allows us to store the Kubernetes deployment scripts without usecase specific information in Git.
        Helm will then have a values.yaml file that defines all the customizable fields and settings to use when deploying to Kubernetes.
    * Corda Enterprise jars downloaded and stored in 'bin' folder
        Downloading these requires a valid Corda Enterprise License agreement.
        If you have the necessary login details you can download them directly from: `Corda Artifactory <https://ci-artifactory.corda.r3cev.com/artifactory/webapp/#/home>`_
       
Setup
~~~~~

Setting up the relevant cloud services is currently left to the reader, this may change in future versions of the scripts.
Having said that though, these are the services you will need to have set up in order to execute the deployment scripts correctly.

Azure Kubernetes Service (AKS)
------------------------------

This is the main Kubernetes cluster that we will be using. Setting up the AKS will also set up a NodePool resource group. The NodePool should also have a few public IP addresses configured as Front End IP addresses for the AKS cluster.
A good guide to follow for setting up AKS: `Quickstart: Deploy an Azure Kubernetes Service cluster using the Azure CLI <https://docs.microsoft.com/en-us/azure/aks/kubernetes-walkthrough>`_
Worth reading the ACR section at the same time to combine the knowledge and setup process.

Azure Container Registry (ACR)
------------------------------

The ACR provides the Docker images for the AKS to use. Please make sure that the AKS can connect to the ACR using appropriate Service Principals. See: `Azure Container Registry authentication with service principals <https://docs.microsoft.com/en-us/azure/container-registry/container-registry-auth-service-principal>`_. 
Guide for setting up ACR: `Tutorial: Deploy and use Azure Container Registry <https://docs.microsoft.com/en-us/azure/aks/tutorial-kubernetes-prepare-acr>`_
Guide for connecting ACR and AKS: `Authenticate with Azure Container Registry from Azure Kubernetes Service <https://docs.microsoft.com/en-us/azure/aks/cluster-container-registry-integration>`_
Worth reading the AKS section at the same time to combine the knowledge and setup process.

Azure Service Principals
------------------------

Service Principals is Azures way of delegating permissions between different services within Azure. There should be at least one Service Principal for AKS which can access ACR to pull the Docker images from there.
Here is a guide to get your started on SPs: `Service principals with Azure Kubernetes Service (AKS) <https://docs.microsoft.com/en-us/azure/aks/kubernetes-service-principal>`_

Azure Storage Account
---------------------

In addition to that there should be a storage account that will host the persistent volumes (File storage). 
Guide on setting up Storage Accounts: `Create an Azure Storage account <https://docs.microsoft.com/en-us/azure/storage/common/storage-account-create?tabs=azure-portal>`_

Public IP addresses
-------------------

You should have a few static public IP addresses available for each deployment. One for the Node to accept incoming RPC connections from an UI level and another one if running the Float component within the cluster, this would then be the public IP address that other nodes would see and connect to.
A guide on setting up Public IP addresses in Azure: `Create, change, or delete a public IP address <https://docs.microsoft.com/en-us/azure/virtual-network/virtual-network-public-ip-address>`_

Docker image generation
~~~~~~~~~~~~~~~~~~~~~~~

We need to have the relevant Docker images in the Container Registry for Kubernetes to access.
This is accomplished by the following two scripts in the folder ``docker-images``:

* build_docker_images.sh
    Will compile the Dockerfiles into Docker images and tag them with the appropriate tags (that are customisable).
* push_docker_images.sh
    Pushes the created Docker images to the assigned Docker Registry.

Both of the above scripts rely on configuration settings in the file ``docker_config.sh``. The main variables to set in this file are ``DOCKER_REGISTRY``, ``HEALTH_CHECK_VERSION`` and ``VERSION``, the rest of the options can use their default values.

Public Key Infrastructure (PKI) generation
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Some parts of the deployment use independent PKI structure. This is true for the Corda Firewall. The two components of the Corda Firewall, the Bridge and the Float communicate with each other using mutually authenticated TLS using a common certificate hierarchy with a shared trust root.
One way to generate this certificate hierarchy is by use of the tools located in the folder ``corda-pki-generator``.
This is just an example for setting up the necessary PKI structure and does not support storing the keys in HSMs, for that additional work is required and that is expected in an upcoming version of the scripts.
