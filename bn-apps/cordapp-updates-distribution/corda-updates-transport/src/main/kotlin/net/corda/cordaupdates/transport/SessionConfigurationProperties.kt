package net.corda.cordaupdates.transport

/**
 * Configuration properties that can be passed via custom Maven Resolver session properties
 */
object SessionConfigurationProperties {
    // reference to an instance of AppServiceHub. AppServiceHub is used by Corda transport to start a data transfer in a separate flow to prevent
    // Maven resolver contents from being checkpointed
    const val APP_SERVICE_HUB = "corda-updates.appServiceHub"
}