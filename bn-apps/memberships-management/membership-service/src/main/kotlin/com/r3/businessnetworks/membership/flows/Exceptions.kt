package com.r3.businessnetworks.membership.flows

import com.r3.businessnetworks.membership.states.MembershipState
import net.corda.core.flows.FlowException
import net.corda.core.identity.Party

sealed class BNMSException(message : String) : FlowException(message)
class NotAMemberException(val counterparty : Party) : BNMSException("Counterparty $counterparty is not a member of this business network")
class MembershipNotActiveException(val counterparty : Party) : BNMSException("Counterparty's $counterparty membership in this business network is not active")
class NotBNOException(val membership : MembershipState<Any>) : BNMSException("This node is not the business network operator for this membership")
class BNONotWhitelisted(val bno : Party) : BNMSException("Party $bno is not whitelisted as BNO")
class MembershipNotFound(val party : Party) : BNMSException("Membership for party $party not found")
