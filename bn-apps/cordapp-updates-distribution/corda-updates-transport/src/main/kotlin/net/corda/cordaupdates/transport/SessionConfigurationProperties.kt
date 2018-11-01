package net.corda.cordaupdates.transport

/**
 * Configuration properties that can be passed via custom Maven Resolver session properties
 */
object SessionConfigurationProperties {
    // reference to an instance of [AppServiceHub]
    const val APP_SERVICE_HUB = "corda-updates.appServiceHub"
    const val RPC_HOST = "corda-updates.rpcHost"
    const val RPC_PORT = "corda-updates.rpcPort"
    const val RPC_USERNAME = "corda-updates.rpcUsername"
    const val RPC_PASSWORD = "corda-updates.rpcPassword"
    // this property is used by corda-auto transport to switch between corda-flows and corda-rpc transports based on the invoking context
    const val MODE = "corda-updates.mode"
}