package net.corda.businessnetworks.membership.member

import net.corda.businessnetworks.membership.member.service.MemberConfigurationService
import net.corda.businessnetworks.membership.states.MembershipState
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.utilities.loggerFor

object Utils {
    val logger = loggerFor<Utils>()

    /**
     * A convenience method to convert MembershipState<Any> to a MembershipState<T>. All states which metadata is not T will be filtered out.
     */
    inline fun <reified T : Any> Map<Party, StateAndRef<MembershipState<Any>>>.ofType() = this.filterValues {
        val matches = it.state.data.membershipMetadata is T
        if (!matches) {
            logger.warn("Membership ${it.state.data} doesn't match the requested metadata type of ${T::class.java}. Not including into the snapshot.")
        }
        matches
    }.mapValues { it.value as StateAndRef<MembershipState<T>> }


    fun throwExceptionIfNotBNO(bno : Party, serviceHub : ServiceHub) {
        // Only configured BNOs should be accepted
        val configuration = serviceHub.cordaService(MemberConfigurationService::class.java)
        if (bno !in configuration.bnoIdentities()) {
            throw IllegalArgumentException("Invalid BNO identity $bno")
        }
    }
}