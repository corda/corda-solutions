package net.corda.businessnetworks.membership.common

import net.corda.businessnetworks.membership.states.MembershipMetadata
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class PartyAndMembershipMetadata(val party : Party, val membershipMetadata: MembershipMetadata) {}