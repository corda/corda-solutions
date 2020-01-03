Start Components
================

The components should be started in the following order:

1. Float
#. Node
#. Bridge

Please note that corda-bridgeserver.jar is used by both Bridge and Float. The JAR file  assumes its input is bridge.conf however this may be overridden with the --config-file parameter so you can designate whatever config file name you wish to use.

Starting Float
~~~~~~~~~~~~~~

On the Float VM run:

/usr/bin/java -Xmx1024m -jar /opt/corda/corda-bridgeserver-3.2.jar --config-file float.conf

You should see the following output:

.. sourcecode:: shell

    FloatSupervisorService: active = false
    FloatSupervisorService: active = true

..


Starting Corda Node
~~~~~~~~~~~~~~~~~~~

On the Node VM run:

/usr/bin/java -Xmx2048m -jar /opt/corda/corda-3.2.jar --config-file node.conf

.. literalinclude:: resources/nodestart.conf
    :language: javascript



Starting Bridge
~~~~~~~~~~~~~~~

On the Bridge VM run:

/usr/bin/java -Xmx1024m -jar /opt/corda/corda-bridgeserver-3.2.jar

You should see the following output in the Bridge:

.. sourcecode:: shell

    BridgeSupervisorService: active = false
    BridgeSupervisorService: active = true

..

You should see the following output in the Float log:

.. sourcecode:: shell

    Now listening for incoming connections on VM-Of-Float-Public-IP:Port

..
