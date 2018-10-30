package net.corda.businessnetworks.ticketing.entity

import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class PartyAndSubject<T>(val party : Party, val subject : T)