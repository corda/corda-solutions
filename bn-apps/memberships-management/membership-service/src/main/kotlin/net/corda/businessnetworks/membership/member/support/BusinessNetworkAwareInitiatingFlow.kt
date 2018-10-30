package net.corda.businessnetworks.membership.member.support

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.member.Utils.throwExceptionIfNotBNO
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party

abstract class BusinessNetworkAwareInitiatingFlow<out T>(val bno : Party) : FlowLogic<T>() {
    @Suspendable
    override fun call() : T {
        throwExceptionIfNotBNO(bno, serviceHub)
        return afterBNOIdentityVerified()
    }

    @Suspendable
    abstract fun afterBNOIdentityVerified() : T
}