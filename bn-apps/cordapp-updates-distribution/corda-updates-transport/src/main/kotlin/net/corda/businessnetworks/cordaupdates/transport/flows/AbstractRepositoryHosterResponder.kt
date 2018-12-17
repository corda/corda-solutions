package net.corda.businessnetworks.cordaupdates.transport.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.serialization.CordaSerializable

/**
 * This class puts every incoming session through a [SessionFilter] if one has been specified in the configuration.
 * In the case if no [SessionFilter] has been specified - every request will be let through.
 *
 * @throws FlowException if the incoming request doesn't satisfy [SessionFilter] requirements
 *
 * TODO: this logic should be implemented as flow overrides once the feature is available
 */
abstract class AbstractRepositoryHosterResponder<T>(val session : FlowSession) : FlowLogic<T>() {
    @Suspendable
    override fun call() : T {
        val configuration = serviceHub.cordaService(RepositoryHosterConfigurationService::class.java)
        val sessionFilter = configuration.getSessionFilter()
        if (sessionFilter != null && !sessionFilter.isSessionAllowed(session, this)) {
            throw FlowException("Counterparty ${session.counterparty} is not allowed to access repository")
        }
        return postPermissionCheck()
    }

    @Suspendable
    protected abstract fun postPermissionCheck() : T
}

/**
 * Request that is used to get / peek a resource from a remote repository. Repository with [repositoryName] has to be configured at the repository hoster's node
 */
@CordaSerializable
data class ResourceRequest (val repositoryName : String, val resourceLocation : String)
