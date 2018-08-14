package net.corda.businessnetworks.membership.common

import net.corda.core.flows.FlowException
import net.corda.core.identity.Party

class CounterPartyNotAMemberException(val counterParty : Party) : FlowException("Counterparty $counterParty is not a member of this business network")
class CounterPartyMembershipNotActive(val counterParty : Party) : FlowException("Counterparty's $counterParty membership in this business network is not active")