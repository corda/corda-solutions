package net.corda.businessnetworks.ledgersync.states

import net.corda.core.contracts.LinearState
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import org.junit.Test

@Suppress("PrivatePropertyName")
class LedgerStateTest {
    private val ALICE = TestIdentity(CordaX500Name(organisation = "Alice Org", locality = "London", country = "GB"))
    private val BOB = TestIdentity(CordaX500Name(organisation = "Bob Org", locality = "Berlin", country = "DE"))

    @Test
    fun `Both requester and requestee are participants`() {
        val state = LedgerSync.State(ALICE.party, BOB.party)
        assert(state.participants.contains(ALICE.party))
    }

    @Test
    fun `Is a linear state`() {
        assert(LinearState::class.java.isAssignableFrom(LedgerSync.State::class.java))
    }
}