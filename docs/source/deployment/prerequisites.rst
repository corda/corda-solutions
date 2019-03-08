Prerequisites to Deploy Corda & join the  Corda Network
=======================================================

There are a few prerequisites to consider prior to deployment & onboarding to the Corda Network.

1. Request the latest versions of Corda & Corda Firewall Jar Files from R3.

#. Provision the Virtual Machines on which the Corda & Corda JVM's will run. 

#. Provide the Public IP Address of the VM on which your Corda Node will reside.

#. Provide the Public IP Address of the VM on which the Corda Float will reside. 

#. Request your DBA team to provide a database for the Corda Vault in one of the supported platforms.

#. Request your Network Engineers open connections thru the firewall to the following R3 Network components. 
#. Request your Network Engineers provide a HTTP Proxy (connection to Doorman & Network Map) and a SOCKS Proxy for connection to peers on the Corda Network. 

UAT Customer Access Details

- Doorman: uat-doorman1-01.uat.corda.network (51.140.179.54); 80 & 443
- Network Map: uat-netmap1-01.uat.corda.network (51.140.164.141); 80 & 443
- Notary Instance 1: uat-notary1-01.uat.corda.network (51.140.122.218); 10002
- Notary Instance 2: uat-notary1-02.uat.corda.network (51.140.123.38); 10002
- Notary Instance 3: uat-notary1-03.uat.corda.network (51.141.119.136); 10002
- Notary Instance 4: uat-notary1-04.uat.corda.network (51.141.119.123); 10002
- Notary Instance 5: uat-notary1-05.uat.corda.network (168.63.21.151); 10002
- CRL: crl.uat.corda.network (51.140.179.54); 80

PROD Customer Access Details

- Doorman:  prod-doorman2-01.corda.network (52.151.82.134); Port 80 & 443
- Network Map: prod-netmap2-01.corda.network (52.151.84.51); Port 80 & 443
- Notary Instance 1: prod-notary2-01.corda.network (52.151.73.29); Port 10002
- Notary Instance 2: prod-notary2-02.corda.network (52.151.74.212); Port 10002
- Notary Instance 3: prod-notary2-03.corda.network (51.141.78.24) ; Port 10002
- Notary Instance 4: prod-notary2-04.corda.network (51.141.76.53) ; Port 10002
- Notary Instance 5: prod-notary2-05.corda.network (51.144.50.4); Port 10002
- CRL: crl.corda.network; 80
