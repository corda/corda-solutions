package com.r3.businessnetworks.billing.states

import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test
import java.time.Instant

class BillingContractTest {
    private var ledgerServices = MockServices(listOf("com.r3.businessnetworks.billing.states"))
    private val owner = TestIdentity(CordaX500Name.parse("O=Owner,L=London,C=GB"))
    private val issuer = TestIdentity(CordaX500Name.parse("O=Issuer,L=London,C=GB"))
    private val ownerParty = owner.party
    private val issuerParty = issuer.party

    private fun billingState(issuer: Party = issuerParty,
                             owner: Party = ownerParty,
                             issued: Long = 10000L,
                             spent: Long = 0L,
                             expiryDate: Instant? = null) = BillingState(issuer, owner, issued, spent, expiryDate)

    private fun chipState(billingState : BillingState, amount: Long = 1L) = BillingChipState(billingState.owner,
            amount,
            billingState.linearId)

    @Test
    fun `test issuance`() {
        ledgerServices.ledger {
            // Happy path with a pre-allocated metering
            transaction {
                output(BillingContract.CONTRACT_NAME,  billingState())
                command(listOf(issuer.publicKey, owner.publicKey), BillingContract.Commands.Issue())
                this.verifies()
            }
            // Happy path with a post-allocated metering
            transaction {
                output(BillingContract.CONTRACT_NAME,  billingState(issued = 0L))
                command(listOf(issuer.publicKey, owner.publicKey), BillingContract.Commands.Issue())
                this.verifies()
            }
            transaction {
                output(BillingContract.CONTRACT_NAME,  billingState(issued = -1L))
                command(listOf(issuer.publicKey, owner.publicKey), BillingContract.Commands.Issue())
                this.failsWith("Issued amount should not be negative")
            }
            transaction {
                command(listOf(issuer.publicKey, owner.publicKey), BillingContract.Commands.Issue())
                this.failsWith("There should be one output of BillingState type")
            }
            transaction {
                output(BillingContract.CONTRACT_NAME,  billingState(spent = 1L))
                command(listOf(issuer.publicKey, owner.publicKey), BillingContract.Commands.Issue())
                this.failsWith("Spent amount should be zero")
            }
            transaction {
                output(BillingContract.CONTRACT_NAME,  billingState())
                command(listOf(owner.publicKey), BillingContract.Commands.Issue())
                this.failsWith("Both the owner and the issuer should be signers")
            }
            transaction {
                output(BillingContract.CONTRACT_NAME,  billingState())
                input(BillingContract.CONTRACT_NAME,  billingState())
                command(listOf(owner.publicKey), BillingContract.Commands.Issue())
                this.failsWith("There should be no inputs of BillingState and BillingChipState types")
            }
            transaction {
                val billingState = billingState()
                output(BillingContract.CONTRACT_NAME,  billingState)
                input(BillingContract.CONTRACT_NAME,  chipState(billingState))
                command(listOf(owner.publicKey), BillingContract.Commands.Issue())
                this.failsWith("There should be no inputs of BillingState and BillingChipState types")
            }
            transaction {
                val billingState = billingState()
                output(BillingContract.CONTRACT_NAME,  billingState)
                output(BillingContract.CONTRACT_NAME,  chipState(billingState))
                command(listOf(owner.publicKey), BillingContract.Commands.Issue())
                this.failsWith("There should be no outputs of BillingChipState type" )
            }
        }
    }

    @Test
    fun `test chip off`() {
        val inputBillingState = billingState()
        val outputChipState = chipState(inputBillingState)
        val outputBillingState = inputBillingState.copy(spent = inputBillingState.spent + outputChipState.amount)
        ledgerServices.ledger {
            // Happy path with a pre-allocated billing
            transaction {
                output(BillingContract.CONTRACT_NAME,  outputBillingState)
                output(BillingContract.CONTRACT_NAME,  outputChipState)
                input(BillingContract.CONTRACT_NAME,  inputBillingState)
                command(listOf(owner.publicKey), BillingContract.Commands.ChipOff())
                this.verifies()
            }
            // Happy path with a post billing
            transaction {
                output(BillingContract.CONTRACT_NAME,  outputBillingState.copy(issued = 0L))
                output(BillingContract.CONTRACT_NAME,  outputChipState)
                input(BillingContract.CONTRACT_NAME,  inputBillingState.copy(issued = 0L))
                command(listOf(owner.publicKey), BillingContract.Commands.ChipOff())
                this.verifies()
            }
            // Happy path with a time constraint
            transaction {
                val expiryDate = Instant.now()
                output(BillingContract.CONTRACT_NAME,  outputBillingState.copy(expiryDate = expiryDate))
                output(BillingContract.CONTRACT_NAME,  outputChipState)
                input(BillingContract.CONTRACT_NAME,  inputBillingState.copy(expiryDate = expiryDate))
                command(listOf(owner.publicKey), BillingContract.Commands.ChipOff())
                timeWindow(expiryDate)
                this.verifies()
            }
            transaction {
                output(BillingContract.CONTRACT_NAME,  outputBillingState)
                output(BillingContract.CONTRACT_NAME,  outputChipState)
                input(BillingContract.CONTRACT_NAME,  inputBillingState)
                input(BillingContract.CONTRACT_NAME, outputChipState)
                command(listOf(owner.publicKey), BillingContract.Commands.ChipOff())
                this.failsWith("There should be no inputs of BillingChipState type")
            }
            transaction {
                output(BillingContract.CONTRACT_NAME,  outputBillingState)
                output(BillingContract.CONTRACT_NAME,  outputChipState)
                input(BillingContract.CONTRACT_NAME,  inputBillingState)
                input(BillingContract.CONTRACT_NAME,  inputBillingState)
                command(listOf(owner.publicKey), BillingContract.Commands.ChipOff())
                this.failsWith("There should be one input of BillingState type")
            }
            transaction {
                output(BillingContract.CONTRACT_NAME,  outputBillingState)
                output(BillingContract.CONTRACT_NAME,  outputBillingState)
                output(BillingContract.CONTRACT_NAME,  outputChipState)
                input(BillingContract.CONTRACT_NAME,  inputBillingState)
                command(listOf(owner.publicKey), BillingContract.Commands.ChipOff())
                this.failsWith("There should be one output of BillingState type")
            }
            transaction {
                output(BillingContract.CONTRACT_NAME,  outputBillingState)
                input(BillingContract.CONTRACT_NAME,  inputBillingState)
                command(listOf(owner.publicKey), BillingContract.Commands.ChipOff())
                this.failsWith("There should be at least one output of BillingChipState type")
            }
            transaction {
                output(BillingContract.CONTRACT_NAME,  outputBillingState)
                output(BillingContract.CONTRACT_NAME,  outputChipState)
                input(BillingContract.CONTRACT_NAME,  inputBillingState)
                command(listOf(owner.publicKey), BillingContract.Commands.ChipOff())
                this.failsWith("Input and output BillingStates should be equal except the `spent` field")
            }

        }
    }
}