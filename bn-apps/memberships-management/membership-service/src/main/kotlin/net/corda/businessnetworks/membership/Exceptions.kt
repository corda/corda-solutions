package net.corda.businessnetworks.membership

import net.corda.businessnetworks.membership.states.MembershipState
import net.corda.core.flows.FlowException
import net.corda.core.identity.Party

class NotAMemberException(val counterparty : Party) : FlowException("Counterparty $counterparty is not a member of this business network")
class MembershipNotActiveException(val counterparty : Party) : FlowException("Counterparty's $counterparty membership in this business network is not active")
class NotBNOException(val membership : MembershipState<Any>) : FlowException("This node is not the business network operator of this membership")
class MembershipNotFound(val party : Party) : FlowException("Membership for party $party not found")
