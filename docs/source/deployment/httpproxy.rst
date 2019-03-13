Using HTTP Proxy with Corda
============================

Many financial institutions will use an HTTP Proxy Server to monitor connections going out to the Internet. 

Corda facilitates the use of an HTTP Proxy to access the Doorman & Network map via HTTPS "get" requests.

The following is an example of how to set up a Squid Proxy Server and start the Corda Node to point to it as a "tunnel" to connect to Doorman and Network Map.

1. Prerequisite is a VM 2 CPU Core & 2 GB RAM running Ubuntu 18.x.

2. ssh into the VM where you want to install the Proxy Server and run the following:

- sudo apt update
- sudo apt -y install squid

3. You should edit /etc/squid/squid.conf and add the following entries

.. literalinclude:: ./squidconfig.conf
    :language: javascript

4. Once Squid is successfully installed run:

- sudo systemctl start squid
- sudo systemctl enable squid
- sudo systemctl status squid

5. If Squid starts successfully you will see an output similar to this


.. literalinclude:: ./squidstatus.conf
    :language: javascript

6. At this point you can ssh to the VM where the Corda Node is installed and run the following:

- java -Dhttps.proxyHost=your-firewall-proxy -Dhttps.proxyPort=8080 -jar corda.jar 

7. If the Corda Node starts up sucessfully you can then check /var/log/squid/access.log and you should see output as follows:


.. literalinclude:: ./access.conf
    :language: javascript
