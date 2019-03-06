Node Registration Overview
==========================

The diagram above illustrates the process of joining the Corda Network in UAT & PROD. The first few steps may happen any time before the actual Corda Node registration in order to retrieve the public key. All new joiners to the network must supply their node host public IP address to be whitelisted against the UAT 3.2 Network Map.

- Send email using email address representing the enterprise whose Legal Identity will be registered, providing IP address of machine that will listen for traffic (Float).
- Your IP address will be whitelisted.
- You will receive a network-root-truststore.jks file containing the public key certificate from the Corda compatibility zone to which you are registering, e.g., Corda Network UAT or Corda Network. This is the file you will use later in the process to register your Corda Node once it is configured.

.. image:: registration.png
   :scale: 100%
   :align: left
