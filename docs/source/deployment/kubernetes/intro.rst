Corda Kubernetes Deployment option
==================================

.. line-block::
    Kubernetes shines in orchestrating complex connection scenarios between multiple components and allowing these components to self-heal and recover from errors.
    The Corda Node is quite a complex system to deploy when it is deployed with Corda Firewall and potentially utilising HSM for storing private key material.
    This Kubernetes deployment is created to show you how to set up the different components in a Kubernetes cluster while still catering to best security practices.
    
    Having said that, this deployment is still in its early stages and should be considered **experimental**.


.. toctree::
   :maxdepth: 1

   considerations
   architecture-overview
   prerequisites
   corda-kubernetes-deployment-option   
