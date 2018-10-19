package net.corda.cordaupdates.transport.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession

/**
 * This class puts every incoming session through a [SessionFilter] if one has been specified in the configuration.
 * In the case if no [SessionFilter] has been specified - every request will be let through.
 *
 * @throws FlowException if the incoming request doesn't satisfy [SessionFilter] requirements
 * TODO: this logic should be moved to the flow overrides once the feature is available
 */
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