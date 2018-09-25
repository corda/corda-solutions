package net.corda.businessnetworks.membership

import net.corda.businessnetworks.membership.states.Membership
import net.corda.core.flows.FlowException
import net.corda.core.identity.Party

class NotAMemberException(val counterParty : Party) : FlowException("Counterparty $counterParty is not a member of this business network")
class MembershipNotActiveException(val counterParty : Party) : FlowException("Counterparty's $counterParty membership in this business network is not active")
class NotBNOException(val membership : Membership.State) : FlowException("This node is not the business network operator of this membership")
class MembershipNotFound(val party : Party) : FlowException("Membership for party $party not found")
