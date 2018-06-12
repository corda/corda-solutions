![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

# Application level metric collection


## Document Control

| Title                | Application level metric collection |
| -------------------- | --- |
| Date                 | 12 June 2018 |
| Author               | Romans Markuns |
| Distribution         | Design Review Board, Product Management, Solutions Engineering, Platform Delivery |
| Corda target version | OS |


## Epigraph

*"You can't control what you can't measure"* - Tom DeMarco, Controlling
Software Projects (1982)


## Glossary

* *TX* - Transaction


## Overview

The goal of this project is to provide CorDapp developers with unified way to
measure use-case agnostic parameters of their applications, e.g: performance
statistics, transaction chain parameters, data consumption rates, etc.

Note that initial stages of implementations will only consider basic performance
statistics of CorDapp flow - network, database and computation latencies.
Depending on demand and provided solution, this design might be extended to
provide a framework to exchange use-case specific metrics, which is currently
out of scope.

Before diving into design, it is worth to ephasise that application level
metrics are different from single node metrics in distributed application
context. In distributed application program flow spans multiple machines over
network, and it is possible to have poor application performance with nodes
that perform outstandingly. That can be caused by unstable network, node chache
invalidation, incorrect transaction batching, suboptimal flow sequience, etc.


## Scope

In-scope:
* Definition of application level metrics
* Design of inter-node metric collection protocol
* Corda node instrumentation
* Metric in-memory storage design and configuration

Out-of-scope (not MVP):
* Metric aggregation inside single node or across nodes
* Metric export to ready solutions (e.g. Prometheus)
* Metric export format
* Metric visualization


## Current situation

Current Corda nodes expose some metrics that might be used for applciation level
analysis.

### Flow durations

By providing custom log4j configuration to node it is possible to fetch flow
durations from logging events.

For example following configuration:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN" monitorInterval="10">
    <Appenders>
        <File name="file" fileName="logs/I-am-The-Log.log">
            <PatternLayout pattern="%d{HH:mm:ss.SSS} - %msg%n"/>
        </File>
    </Appenders>
    <Loggers>
        <Logger name="net.corda.flow" level="debug" additivity="false">
            <AppenderRef ref="file"/>
        </Logger>
        <Root level="error">
            <AppenderRef ref="file"/>
        </Root>
    </Loggers>
</Configuration>
```

will produce a log file with:
```
09:26:28.970 - Calling flow: ...
09:26:28.998 - Calling subflow: net.corda.core.flows.FinalityFlow@4d80934
09:26:29.008 - Calling subflow: net.corda.core.flows.NotaryFlow$Client@1566bb37
09:26:29.012 - sendAndReceive ...
09:26:29.029 - Initiating flow session with party ...
09:26:29.348 - Received ...
09:26:29.350 - Subflow finished with result ...
09:26:29.353 - Subflow finished with result SignedTransaction(id=...)
09:26:29.377 - Flow finished with result OK
```

Whilst good to have these logs are not fit for application level metrics due to:
* they are too granular to understand network/database latencies
* they require all parties to have correct node logging file and Appender
implementation that would parse logging events and forward them through network

### JDBC timings

One can instrument current Corda JDBC driver with logger. That can be done with
following steps:
1. Check out to release branch of Corda
1. Remove default DataSource class from `reference.conf`
1. Add spy as dependency to node/build.gradle : compile 'p6spy:p6spy:3.7.0'
1. Build Corda node with ./gradlew :node:capsule:install
1. Change node.conf to have following lines:
```json
"dataSourceProperties" : {
    "driverClassName" : "com.p6spy.engine.spy.P6SpyDriver",
    "jdbcUrl" : "jdbc:p6spy:h2:file:"${baseDirectory}"/persistence;..."
}
 ```
6. ...
6. Profit in form of spy.log file:
```
1528464750425|0|statement|connection 41|select legalident0_.node_info_id as node_inf1_8_0_, ...
```

This also requires correct configuration from all nodes and on top of that does
not tie JDBC statements to a particular flow/transaction.

### Network connection information

Network latencies can be calculated from ActiveMQ advisory topics:
```
AdvisorySupport.getMessageDeliveredAdvisoryTopic()
AdvisorySupport.getMessageConsumedAdvisoryTopic()
```

## Target solution
## Timeline
## Requirements
## Assumptions
## API extension points
