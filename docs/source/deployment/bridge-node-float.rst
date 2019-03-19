Starting Your Node, Bridge and Float
====================================

The components should be started in the following order:

1. Float
#. Node
#. Bridge


Starting Float
^^^^^^^^^^^^^^

On the Float VM run:

/usr/bin/java -Xmx2048m -jar /opt/corda/corda-bridgeserver-3.2.jar --config-file float.conf

You should see the following output:

.. sourcecode:: shell

    FloatSupervisorService: active = false
    FloatSupervisorService: active = true

..


Starting Corda Node
^^^^^^^^^^^^^^^^^^^

On the Node VM run:

/usr/bin/java -Xmx2048m -jar /opt/corda/corda-3.2.jar

.. literalinclude:: ./nodestart.conf
    :language: javascript




Starting Bridge
^^^^^^^^^^^^^^^

On the Bridge VM run:

/usr/bin/java -Xmx2048m -jar /opt/corda/corda-bridgeserver-3.2.jar

You should see the following output in the Bridge:

.. sourcecode:: shell

BridgeSupervisorService: active = false
BridgeSupervisorService: active = true

..

You should see the following output in the Float log:

.. sourcecode:: shell

Now listening for incoming connections on VM-Of-Float-Public-IP:10010

..
