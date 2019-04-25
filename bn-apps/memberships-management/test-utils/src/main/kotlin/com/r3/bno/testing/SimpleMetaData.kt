package com.r3.bno.testing

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class SimpleMembershipMetadata(val role: String = "", val displayedName: String = "")

@CordaSerializable
data class SomeCustomMembershipMetadata(val someCustomField: String)