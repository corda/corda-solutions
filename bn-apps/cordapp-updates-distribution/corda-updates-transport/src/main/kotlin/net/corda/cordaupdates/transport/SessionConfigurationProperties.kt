package net.corda.cordaupdates.transport

/**
 * Reference to an instance of AppServiceHub that is passed to Maven Resolver via custom session configuration properties.
 * AppServiceHub is used by Corda transport to start a data transfer in a separate flow to prevent Maven resolver contents from being checkpointed.
 */
const val APP_SERVICE_HUB = "corda-updates.appServiceHub"