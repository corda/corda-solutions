package com.r3.businessnetworks.ledgersync

import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.internal.startFlow
import org.junit.Test
import kotlin.test.assertEquals

class ConsistencyTestsWithoutVr : ConsistencyTests() {
    override val cordappPackages: List<String> = listOf(
            "com.r3.businessnetworks.membership",
            "com.r3.businessnetworks.membership.states",
            "com.r3.businessnetworks.ledgersync"
    )

    @Test
    fun `VR is not detected`() {
        val future = node1.fromNetwork().services.startFlow(VaultRecyclerExistFlow()).resultFuture
        mockNetwork.runNetwork()
        assertEquals(false, future.getOrThrow())
    }
}
