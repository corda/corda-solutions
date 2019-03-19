Using Socks Proxy with Corda Bridge
===================================


R3 strongly recommend the use of an SOCKS Proxy in conjunction with the Corda Firewall to access peers on the network for P2P communication. 

SOCKS is a general purpose proxy server that establishes a TCP connection to another server on behalf of a client, then routes all the traffic back and forth between the client and the server. It works for any kind of network protocol on any port. SOCKS Version 5 adds additional support for security and UDP. By contrast an HTTP Proxy only understands HTTP traffic. 

SOCKS works by establishing a TCP connection with another server on the behalf of your client machine. Through this connection, traffic is routed between the client and the server, essentially anonymizing and encrypting your data and your information along the way.

SOCKS proxies provide an improvement over HTTP proxy in terms of speed of data delivery & by preventing data packets being mis-routed or mislabeled. This provides an overall improvement in terms of stability and avoiding data transfer errors that could otherwise happen.  

The additional benefit of utilizing a SOCKS server is that it facilitates organizations enforce security policy and allow applications to reach legitimate external hosts through simple, centrally controlled rule-based settings.



.. literalinclude:: ./socks.conf
    :language: javascript
