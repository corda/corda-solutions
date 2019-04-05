cordaadmin@corda-firewall-proxies:~$ sudo systemctl status squid
● squid.service - LSB: Squid HTTP Proxy version 3.x
   Loaded: loaded (/etc/init.d/squid; generated)
   Active: active (running) since Wed 2019-03-13 18:44:10 UTC; 14min ago
     Docs: man:systemd-sysv-generator(8)
  Process: 14135 ExecStop=/etc/init.d/squid stop (code=exited, status=0/SUCCESS)
  Process: 14197 ExecStart=/etc/init.d/squid start (code=exited, status=0/SUCCESS)
    Tasks: 4 (limit: 4915)
   CGroup: /system.slice/squid.service
           ├─14261 /usr/sbin/squid -YC -f /etc/squid/squid.conf
           ├─14263 (squid-1) -YC -f /etc/squid/squid.conf
           ├─14265 (logfile-daemon) /var/log/squid/access.log
           └─14267 (pinger)

Mar 13 18:44:10 corda-firewall-proxies systemd[1]: Starting LSB: Squid HTTP Proxy version 3.
Mar 13 18:44:10 corda-firewall-proxies squid[14197]:  * Starting Squid HTTP Proxy squid
Mar 13 18:44:10 corda-firewall-proxies squid[14261]: Squid Parent: will start 1 kids
Mar 13 18:44:10 corda-firewall-proxies squid[14197]:    ...done.
Mar 13 18:44:10 corda-firewall-proxies systemd[1]: Started LSB: Squid HTTP Proxy version 3.x
Mar 13 18:44:10 corda-firewall-proxies squid[14261]: Squid Parent: (squid-1) process 14263
