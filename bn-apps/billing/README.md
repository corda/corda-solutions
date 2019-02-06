Billing Service
===============

*Contents of this article assume the reader's familiarity with the concepts of *Business Networks*. Please see [Corda Solutions website](https://solutions.corda.net/business-networks/intro.html) for more information.*

Billing Service can be used for billing and metering on Business Networks. Billing Service has a notion of *Billing Chips* that can be included into Corda transactions which which participants need to pay for. Billing Chips don't cross a single transaction boundaries and hence never cause privacy leaks. All billing chips are attached to the parent billing state, which accumulates total *spent* amount and can be safely reported back to the BNO without leaking any information about where the transactions where the chips have actually been used. Evolution of a Billing State is depicted on the diagram below:

![Billing State Evolution](./resources/billing_state_evolution.png) 

*Please see [Corda Modelling Notation](https://solutions.corda.net/corda-modelling-notation/overview/overview-overview.html) for more information about diagramming for Corda.* 

Billing workflow consists of the following steps:
1. BNO issues BillingState to their Business Network member. Billing state can have a 
2. 