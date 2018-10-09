package net.corda.cordaupdates.transport

/**
 * Custom properties that are recognised by corda-flows and corda-rpc transports
  */
object ConfigurationProperties {
    const val APP_SERVICE_HUB = "corda-updates.appServiceHub"
    const val RPC_HOST = "corda-updates.rpcHost"
    const val RPC_PORT = "corda-updates.rpcPort"
    const val RPC_USERNAME = "corda-updates.rpcUsername"
    const val RPC_PASSWORD = "corda-updates.rpcPassword"
    const val MODE = "corda-updates.mode"
}