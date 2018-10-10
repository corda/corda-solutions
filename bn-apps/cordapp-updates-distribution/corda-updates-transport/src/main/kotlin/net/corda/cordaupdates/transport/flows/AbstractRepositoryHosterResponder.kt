package net.corda.cordaupdates.transport.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession

abstract class AbstractRepositoryHosterResponder<T>(val session : FlowSession) : FlowLogic<T>() {
    @Suspendable
    override fun call() : T {
        val configuration = serviceHub.cordaService(RepositoryHosterConfigurationService::class.java)
        val sessionFilter = configuration.getSessionFilter()
        if (sessionFilter != null && !sessionFilter.isSessionAllowed(session, this)) {
            throw FlowException("Counterparty ${session.counterparty} is not allowed to access repository")
        }
        return doCall()
    }

    @Suspendable
    protected abstract fun doCall() : T
}