Corda Health Checker
====================

This guide is designed to provide detail on how to run the Corda Health Checker tool. 


Overview
--------

The Corda Health Survey is a tool designed to perform connectivity and configuration checks on a Corda Enterprise Node.

The Corda Health Survey verifies:

- Node, Bridge, Float configuration files (both in the v4.x and v3.x formats)
- Network Map/Doorman HTTP endpoints
- AMQP connectivity between Node, Bridge, Float

The tool supports the following deployment configurations:

- Node with internal Artemis broker
- Node with internal Artemis broker, external Bridge
- Node with internal Artemis broker, Bridge, Float (full Corda Enterprise Firewall architecture)
- The tool currently does not support highly-available Corda Node deployments (the required checks should run but the results will be incomplete or inconsistent).

The Corda Health Survey is designed to help operators with initial setup of a Node; it is not meant to be used as an ongoing monitoring tool.

List of Checks
--------------

A detailed list of all the operations performed by the Corda Health Survey includes:

1. Read and parse local Node configuration file, perform sanity check.
2. Connect to the p2p Artemis broker.
3. Broadcast ECHO messages to connected Firewall components (Bridge, Float) and wait for a predetermined number of seconds for replies.
4. Verify HTTP endpoints configured in the Node.
5. Request configuration files from components that replied to the ECHO messages.
6. Cross-validation of received configuration files (e.g. checking for port clashes, port mismatches between node and firewall configuration files).
7. Validate network settings on each component that replied to the ECHO messages.
8. Request runtime information from all component that replied to the ECHO messages.
9. Request firewall service status from non-Node components that replied to the ECHO messages and diagnose services which are reported as inactive.
10. Request a report of inbound/outbound peer connections established during the last X number of seconds (as specified in the auditServiceConfiguration.loggingIntervalSec Firewall configuration option) from non-Node components that replied to the ECHO messages.


- This is not a representation of all reachable peers - only peers that have connected to the non-Node components during the specified time interval will be listed
- For any information to show up, connections need to be initiated or accepted


The tool performs the listed checks incrementally (e.g. if a Node is configured with external bridge but the bridge has not been discovered during the ECHO broadcast, an error will be reported and subsequent firewall checks will be skipped).

Report Format
-------------

After each run, the Corda Health Survey collects and packages up into a .zip file information that R3 Support can use to help a customer with a support request, including:

- An obfuscated version of the config files (i.e., without passwords, etc.)
- Node logs from the last 3 days (if the user is happy to share)
- The version of Corda, Java virtual machine and operating system, networking information with DNS lookups to various endpoints (database, network map, doorman, external addresses)
- A copy of the network parameters file
- A list of installed CorDapps (including file sizes and checksums)
- A list of the files in the drivers directory
- A copy of the Node information file and a list of the ones in the additional-node-infos directory, etc.
- Instead of zipping the reports, operators can print them to a text file using the command line option -t.

The tool optionally allows operators to upload the resulting report to a support ticket. JIRA credentials can either be provided through the environment variables JIRA_USER and JIRA_PASSWORD, or through the prompt.

Disabling the Corda Health Survey in Production
-----------------------------------------------

The tool relies on dedicated Artemis queues to relay configuration and runtime information from the Corda Firewall components. This functionality is enabled by default. After verifying a production deployment, operators are advised to disable the health checking functionality (in order to use the standard Artemis setup for Corda Enterprise) by adding the following entry in the Node configuration file:

.. sourcecode:: shell

	enterpriseConfiguration { healthCheck = false }

And the following entry in the Bridge configuration file:

.. sourcecode:: shell

	healthCheck = false

Running the Corda Health Survey
-------------------------------

The tool runs with the node installation as its current working directory, e.g.:

.. sourcecode:: shell

	$ cd /opt/corda/
	$ java -jar corda-tools-health-survey.jar

Alternatively, the base directory and Node/Bridge/Float configuration paths can be specified as command-line arguments:

base-directory or -d, specifying the path to the Node installation.
node-configuration or -c, specifying the path to the Node configuration file.

These options can be specified together, allowing for setups with Node configuration files under separate directories.

The majority of the checks require the Node (and related Firewall components, if present) to have been started before the tool is run. Please refer to the Corda Enterprise documentation on how to do this.

Other available command-line arguments include:

.. sourcecode:: shell

	--local or -l, verify local Node configuration only without checking Bridge/Float, by default verifies all.
	--jira or -j, prompts user to upload reports a JIRA ticket, by default skips reporting to JIRA.
	--exclude-logs or -e, exclude node log files from ZIP report, by default logs are included in ZIP report.
	--text-format or -t, create report as a single txt file without node log files, by default the output of the tool is packaged in a ZIP file.
	--timeout or -i, override default timeout for sending messages between Node and Firewall components.
	--config-validate or -v, validates Bridge/Float configuration files.
	--bridge-configuration or -b, specifying the path to the Bridge configuration file when used alongside config-validate.
	--float-configuration or -f, specifying the path to the Float configuration file when used alongside config-validate.

Here is sample output from the tool java -jar corda-tools-health-survey-4.1.20190823.jar -t :

.. sourcecode:: shell

	Corda Health Survey Tool 4.1
	~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 	✔ Reporting to file report-20190909-170933.txt
	✔ Collected machine information
	✔ Collected information about Corda installation
	✔ Collected network parameters
	✔ Collected node information file
	✔ Collected additional node information files
	✔ Collected CorDapp information
	✔ Collected censored node configuration
	✔ Collected driver information
	✔ Collected log files
	• Identity Manager status endpoint http://xxx.eastus.cloudapp.azure.com:10000/status returned response  
	• Identity Manager status endpoint http://xxx.eastus.cloudapp.azure.com:10000/status returned response  
	• Identity Manager status endpoint http://xxx.eastus.cloudapp.azure.com:10000/status returned response  
	• Network Map status endpoint http://xxx.cloudapp.azure.com:10001/status returned response code  
	• Network Map status endpoint http://xxx.eastus.cloudapp.azure.com:10001/status returned response code  
	• Network Map status endpoint http://xxx.eastus.cloudapp.azure.com:10001/status returned response code  
	✔ Collected general network information
	✔ Node is configured to use external bridge
	✔ Connected to Artemis Broker
	✔ Initialised tool serialization context
	✔ Node network settings are valid
	✔ Echo message(s) received
	✔ Received ECHO from bridge
	✔ Remote deployment configs collected
	✔ Verified collected configuration files
	✔ Network settings received
	✔ Runtime info collected
	✔ Service status received
	✔ Validated firewall services
	✔ Bridge map received
	✔ Exported report to report-20190909-170933.txt
	A report has been generated and written to disk.
	Path of report: /opt/corda/report-20190909-170933.txt
	Size of report: 69.4 KiB


Example Failure Scenario
------------------------

This scenario assumes Node and Bridge are deployed on the same VM and Float is on separate VM. The Node and Bridge are UP, Float is DOWN. Health Survey Checker will provide the following output

.. sourcecode:: shell

	Corda Health Survey Tool 4.1
	~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 	✔ Reporting to file report-20190909-170933.txt
	✔ Collected machine information
	✔ Collected information about Corda installation
	✔ Collected network parameters
	✔ Collected node information file
	✔ Collected additional node information files
	✔ Collected CorDapp information
	✔ Collected censored node configuration
	✔ Collected driver information
	✔ Collected log files
	• Identity Manager status endpoint http://xxx.eastus.cloudapp.azure.com:10000/status returned response  
	• Identity Manager status endpoint http://xxx.eastus.cloudapp.azure.com:10000/status returned response  
	• Identity Manager status endpoint http://xxx.eastus.cloudapp.azure.com:10000/status returned response  
	• Network Map status endpoint http://xxx.cloudapp.azure.com:10001/status returned response code  
	• Network Map status endpoint http://xxx.eastus.cloudapp.azure.com:10001/status returned response code  
	• Network Map status endpoint http://xxx.eastus.cloudapp.azure.com:10001/status returned response code  
	✔ Collected general network information
	✔ Node is configured to use external bridge
	✔ Connected to Artemis Broker
	✔ Initialised tool serialization context
	✔ Node network settings are valid
	✔ Echo message(s) received
	✔ Received ECHO from bridge
	✔ Remote deployment configs collected
	✘ Float config not found, but expected
	✔ Verified collected configuration files
	✔ Network settings received
	✔ Runtime info collected
	✔ Service status received
	✘ One or more firewall services are reported as inactive
	✔ Validated firewall services
	✔ Bridge map received
	✔ Exported report to report-20190910-094824.txt

 A report has been generated and written to disk.
 Path of report: /opt/corda/report-20190910-094824.txt
 Size of report: 54.6 KiB
 
 
 This is an indicator for the operator to investigate Float status and restart the Float. Operator can then re-run the Corda Healthchecker Toool to confirm end to end connectivity.
