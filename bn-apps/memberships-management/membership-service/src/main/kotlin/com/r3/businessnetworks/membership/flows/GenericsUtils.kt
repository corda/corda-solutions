package com.r3.businessnetworks.membership.flows

import com.r3.businessnetworks.membership.states.MembershipState
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.internal.location

fun StateAndRef<MembershipState<Any>>.getAttachmentIdForGenericParam(): SecureHash {
    return this.state.data.getAttachmentIdForGenericParam()
}

fun MembershipState<Any>.getAttachmentIdForGenericParam(): SecureHash {
    val bytesOfCordapp = this.membershipMetadata.javaClass.location.readBytes()
    return bytesOfCordapp.sha256()
}