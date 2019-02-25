package com.r3.businessnetworks.membership.flows.member.support

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.flows.member.Utils.throwExceptionIfNotBNO
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party

/**
 * This flow is supposed to be extended by Business Network members for any member->BNO communications.
 * The flow verifies that the BNO is whitelisted in member's cordapp configuration.
 */
abstract class BusinessNetworkAwareInitiatingFlow<out T>(val bno : Party) : FlowLogic<T>() {
    @Suspendable
    override fun call() : T {
        throwExceptionIfNotBNO(bno, serviceHub)
        return afterBNOIdentityVerified()
    }

    @Suspendable
    abstract fun afterBNOIdentityVerified() : T
}