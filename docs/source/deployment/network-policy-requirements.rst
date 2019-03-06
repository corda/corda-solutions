Network Policy Requirements
===========================

Connections with the Corda Network Doorman and Network Map services (inbound and outbound traffic) will be over HTTP/HTTPS on ports 80 and 443.

Connections with peer Corda Nodes (including Notaries) will happen on a peer-to-peer connection using AMQP/TLS typically in a port range of 10000 - 10099, though the ports used by other nodes is determined by the node owner.

Connections with local applications connecting with the CorDapp via the Corda Node happen over RPC.

Administrative logins with the Corda Node happen via ssh whose port is configured in the node.conf file, typically port 2222.

