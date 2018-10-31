package net.corda.businessnetworks.membership.bno.service

import com.typesafe.config.ConfigFactory
import net.corda.businessnetworks.membership.ConfigUtils.loadConfig
import net.corda.businessnetworks.membership.bno.extension.MembershipAutoAcceptor
import net.corda.businessnetworks.membership.states.MembershipContract
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import java.io.File

/**
 * Configuration that is used by BNO app. The configuration is red from cordapps/config/membership-service.conf with a fallback to
 * membership-service.conf on the classpath.
 */
@CordaService
class BNOConfigurationService(private val serviceHub : ServiceHub) : SingletonSerializeAsToken() {
    companion object {
        const val NOTARY_NAME = "notaryName"
        // Specifies the class for delegating BNO decisions to
        const val MEMBERSHIP_AUTO_ACCEPTOR = "membershipAutoAcceptor"
        // Name of the contract class to verify membership transactions with. Exists to let users to provide their own implementations
        // in the case if they would like to extend MembershipContract functionality to contractually verify membership metadata evolution.
        // Defaults to MembershipContract.CONTRACT_NAME
        const val MEMBERSHIP_CONTRACT_NAME = "membershipContractName"
    }

    private var _config = loadConfig()

    private fun notaryName() : CordaX500Name = CordaX500Name.parse(_config.getString(NOTARY_NAME))
    fun notaryParty() = serviceHub.networkMapCache.getNotary(notaryName())
            ?: throw IllegalArgumentException("Notary ${notaryName()} has not been found on the network")

    fun membershipContractName() = if (_config.hasPath(MEMBERSHIP_CONTRACT_NAME)) _config.getString(MEMBERSHIP_CONTRACT_NAME)!! else MembershipContract.CONTRACT_NAME

    fun getMembershipAutoAcceptor() : MembershipAutoAcceptor? {
        return if (_config.hasPath(MEMBERSHIP_AUTO_ACCEPTOR)) {
            val className = _config.getString(MEMBERSHIP_AUTO_ACCEPTOR)
            val clazz = Class.forName(className)
            clazz.newInstance() as MembershipAutoAcceptor
        } else null
    }

    fun reloadConfigurationFromFile(file : File) {
        _config = ConfigFactory.parseFile(file)
    }
}