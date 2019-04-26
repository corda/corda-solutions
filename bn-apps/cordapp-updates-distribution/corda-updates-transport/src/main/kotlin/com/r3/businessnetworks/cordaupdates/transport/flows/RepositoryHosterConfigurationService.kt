package com.r3.businessnetworks.cordaupdates.transport.flows

import com.r3.businessnetworks.utilities.AbstractConfigurationService
import net.corda.core.flows.FlowException
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService

/**
 * Configuration for the repository hoster.
 *
 * TODO: replace with serviceHub.getAppContext().config when released
 */
@CordaService
class RepositoryHosterConfigurationService(private val serviceHub : AppServiceHub) : AbstractConfigurationService(serviceHub, "corda-updates-app") {
    companion object {
        const val REPOSITORIES = "repositories"
    }

    override fun bnoName() : CordaX500Name = throw NotImplementedError("This method should not be used")
    override fun notaryName() = throw NotImplementedError("This method should not be used")

    /**
     * URL of the remote repository to fetch an artifact from. Supports -http and -file based transports
     */
    fun repositoryUrl(repoName : String) : String {
        val config = _config ?: throw IllegalArgumentException("Configuration for corda-updates is missing")
        // throwing an IllegalArgumentException here because the configuration has to contain "repositories" section.
        // If it doesn't contain - that would mean that the node has been configured incorrectly (akin to 5xx HTTP error)
        if (!config.hasPath(REPOSITORIES)) {
            throw IllegalArgumentException("$REPOSITORIES attribute is missing from CorDapp config")
        }
        val repoConfig = config.getConfig(REPOSITORIES)!!
        // throwing FlowException here to let the counterparty know that the repository name they provided is invalid (akin to 4xx HTTP error)
        if (!repoConfig.hasPath(repoName)) {
            throw FlowException("Repository $repoName does not exist")
        }
        return repoConfig.getString(repoName)!!
    }
}

