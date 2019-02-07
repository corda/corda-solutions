package com.r3.businessnetworks.billing.states

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.TestIdentity
import net.corda.testing.dsl.TransactionDSL
import net.corda.testing.dsl.TransactionDSLInterpreter
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import net.corda.testing.node.makeTestIdentityService
import org.apache.commons.lang.RandomStringUtils
import org.apache.commons.lang.math.RandomUtils
import org.junit.Test
import java.time.Instant

class BillingContractTest {
    private var ledgerServices = MockServices(listOf("com.r3.businessnetworks.billing.states"),
            CordaX500Name("TestIdentity", "", "GB"),
            makeTestIdentityService(),
            testNetworkParameters(minimumPlatformVersion = 4))
    private val owner = TestIdentity(CordaX500Name.parse("O=Owner,L=London,C=GB"))
    private val issuer = TestIdentity(CordaX500Name.parse("O=Issuer,L=London,C=GB"))
    private val someoneElse = TestIdentity(CordaX500Name.parse("O=Someone Else,L=London,C=GB"))
    private val ownerParty = owner.party
    private val issuerParty = issuer.party
    private val someoneElsesParty = someoneElse.party

    private fun billingState(issuer: Party = issuerParty,
                             owner: Party = ownerParty,
                             issued: Long = 10000L,
                             spent: Long = 0L,
                             status: BillingStateStatus = BillingStateStatus.ACTIVE,
                             expiryDate: Instant? = null) = BillingState(issuer, owner, issued, spent, status, expiryDate)

    @Test
    fun `test issuance`() {
        ledgerServices.ledger {
            // Happy path with bounded state
            transaction {
                output(BillingContract.CONTRACT_NAME,  billingState())
                command(listOf(issuer.publicKey, owner.publicKey), BillingContract.Commands.Issue())
                this.verifies()
            }
            // Happy path with unbounded state
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
                this.failsWith("There should be no inputs")
            }
            transaction {
                val billingState = billingState()
                output(BillingContract.CONTRACT_NAME, billingState)
                output(BillingContract.CONTRACT_NAME, billingState.chipOff(1L).second)
                command(listOf(owner.publicKey), BillingContract.Commands.Issue())
                this.failsWith("There should be a single output of BillingState type" )
            }
            transaction {
                output(BillingContract.CONTRACT_NAME,  billingState(status = BillingStateStatus.RETURNED))
                command(listOf(issuer.publicKey, owner.publicKey), BillingContract.Commands.Issue())
                this.failsWith("BillingState status should be ACTIVE")
            }

        }
    }

    @Test
    fun `test chip off`() {
        val inputBillingState = billingState()
        val (outputBillingState, outputChipState) = inputBillingState.chipOff(1L)
        ledgerServices.ledger {
            // Happy path with bounded state
            transaction {
                output(BillingContract.CONTRACT_NAME,  outputBillingState)
                output(BillingContract.CONTRACT_NAME,  outputChipState)
                input(BillingContract.CONTRACT_NAME,  inputBillingState)
                command(listOf(owner.publicKey), BillingContract.Commands.ChipOff())
                this.verifies()
            }
            // Happy path with unbounded state
            transaction {
                output(BillingContract.CONTRACT_NAME,  outputBillingState.copy(issued = 0L))
                output(BillingContract.CONTRACT_NAME,  outputChipState)
                input(BillingContract.CONTRACT_NAME,  inputBillingState.copy(issued = 0L))
                command(listOf(owner.publicKey), BillingContract.Commands.ChipOff())
                this.verifies()
            }
            // Happy path with multiple chip states
            transaction {
                output(BillingContract.CONTRACT_NAME,  outputBillingState.copy(spent = outputBillingState.spent + outputChipState.amount))
                output(BillingContract.CONTRACT_NAME,  outputChipState)
                output(BillingContract.CONTRACT_NAME,  outputChipState)
                input(BillingContract.CONTRACT_NAME,  inputBillingState)
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
                timeWindow(expiryDate.minusSeconds(50))
                this.verifies()
            }
            transaction {
                output(BillingContract.CONTRACT_NAME,  outputBillingState)
                output(BillingContract.CONTRACT_NAME,  outputChipState)
                input(BillingContract.CONTRACT_NAME,  inputBillingState)
                input(BillingContract.CONTRACT_NAME, outputChipState)
                command(listOf(owner.publicKey), BillingContract.Commands.ChipOff())
                this.failsWith("There should be a single input of BillingState type")
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
                output(BillingContract.CONTRACT_NAME,  outputBillingState.copy(issued = inputBillingState.issued + 1L))
                output(BillingContract.CONTRACT_NAME,  outputChipState)
                input(BillingContract.CONTRACT_NAME,  inputBillingState)
                command(listOf(owner.publicKey), BillingContract.Commands.ChipOff())
                this.failsWith("Input and output BillingStates should be equal except the `spent` field")
            }
            transaction {
                output(BillingContract.CONTRACT_NAME,  outputBillingState)
                output(BillingContract.CONTRACT_NAME,  outputChipState)
                input(BillingContract.CONTRACT_NAME,  inputBillingState)
                command(listOf(issuer.publicKey), BillingContract.Commands.ChipOff())
                this.failsWith("Only owner should be a signer")
            }
            transaction {
                output(BillingContract.CONTRACT_NAME,  outputBillingState)
                output(BillingContract.CONTRACT_NAME,  outputChipState.copy(owner = someoneElsesParty))
                input(BillingContract.CONTRACT_NAME,  inputBillingState)
                command(listOf(owner.publicKey), BillingContract.Commands.ChipOff())
                this.failsWith("Owner of BillingChips should match the BillingStates")
            }
            transaction {
                output(BillingContract.CONTRACT_NAME,  outputBillingState)
                output(BillingContract.CONTRACT_NAME,  outputChipState.copy(billingStateLinearId = UniqueIdentifier()))
                input(BillingContract.CONTRACT_NAME,  inputBillingState)
                command(listOf(owner.publicKey), BillingContract.Commands.ChipOff())
                this.failsWith("Linear id BillingChips should match the BillingStates")
            }
            transaction {
                output(BillingContract.CONTRACT_NAME,  outputBillingState)
                output(BillingContract.CONTRACT_NAME,  outputChipState.copy(amount = -1L))
                input(BillingContract.CONTRACT_NAME,  inputBillingState)
                command(listOf(owner.publicKey), BillingContract.Commands.ChipOff())
                this.failsWith("Amount of BillingChips should be positive")
            }
            transaction {
                output(BillingContract.CONTRACT_NAME,  outputBillingState)
                output(BillingContract.CONTRACT_NAME,  outputChipState.copy(amount = Long.MAX_VALUE - 1))
                output(BillingContract.CONTRACT_NAME,  outputChipState.copy(amount = 10L))
                input(BillingContract.CONTRACT_NAME,  inputBillingState)
                command(listOf(owner.publicKey), BillingContract.Commands.ChipOff())
                this.failsWith("Total chip off value should not exceed Long.MAX_VALUE")
            }
            transaction {
                output(BillingContract.CONTRACT_NAME,  outputBillingState.copy(spent = outputBillingState.spent + 1))
                output(BillingContract.CONTRACT_NAME,  outputChipState)
                input(BillingContract.CONTRACT_NAME,  inputBillingState)
                command(listOf(owner.publicKey), BillingContract.Commands.ChipOff())
                this.failsWith("Spent amount of the output BillingState should be incremented on the total of the chip off value")
            }
            transaction {
                output(BillingContract.CONTRACT_NAME,  outputBillingState.copy(spent = 10001L))
                output(BillingContract.CONTRACT_NAME,  outputChipState.copy(amount = 10001L))
                input(BillingContract.CONTRACT_NAME,  inputBillingState)
                command(listOf(owner.publicKey), BillingContract.Commands.ChipOff())
                this.failsWith("Spent amount of the output BillingState should be less or equal to the issued")
            }
            transaction {
                val expiryDate = Instant.now()
                output(BillingContract.CONTRACT_NAME,  outputBillingState.copy(expiryDate = expiryDate))
                output(BillingContract.CONTRACT_NAME,  outputChipState)
                input(BillingContract.CONTRACT_NAME,  inputBillingState.copy(expiryDate = expiryDate))
                timeWindow(Instant.now())
                command(listOf(owner.publicKey), BillingContract.Commands.ChipOff())
                this.failsWith("Output BillingState expiry date should be within the specified time window")
            }
            transaction {
                output(BillingContract.CONTRACT_NAME,  outputBillingState.copy(status = BillingStateStatus.RETURNED))
                output(BillingContract.CONTRACT_NAME,  outputChipState)
                input(BillingContract.CONTRACT_NAME,  inputBillingState.copy(status = BillingStateStatus.RETURNED))
                command(listOf(owner.publicKey), BillingContract.Commands.ChipOff())
                this.failsWith("Input BillingState status should be ACTIVE")
            }
        }
    }

    @Test
    fun `test use chip`() {
        ledgerServices.ledger {
            // Happy path
            transaction {
                generateUseChipTransaction(this)
                verifies()
            }
            // happy path with time window
            transaction {
                generateUseChipTransaction(this, addTimeWindow = true)
                verifies()
            }
            transaction {
                generateUseChipTransaction(this)
                command(ownerParty.owningKey, BillingContract.Commands.Issue())
                failsWith("UseChip transaction can contain only UseChip Billing commands")
            }
            transaction {
                generateUseChipTransaction(this)
                input(BillingContract.CONTRACT_NAME, billingState())
                failsWith("UseChip transaction should not have BillingStates in inputs")
            }
            transaction {
                generateUseChipTransaction(this)
                output(BillingContract.CONTRACT_NAME, billingState())
                failsWith("UseChip transaction should not have BillingStates in outputs")
            }
            transaction {
                generateUseChipTransaction(this)
                output(BillingContract.CONTRACT_NAME, billingState().chipOff(1L).second)
                failsWith("UseChip transaction should not have BillingChipState in outputs")
            }
            transaction {
                generateUseChipTransaction(this)
                command(ownerParty.owningKey, BillingContract.Commands.UseChip(someoneElsesParty))
                failsWith("UseChip command owner should be the only signer")
            }
            transaction {
                val billingState = billingState()
                billingState.chipOff(1L)
                input(BillingContract.CONTRACT_NAME, billingState.chipOff(1L).second)
                reference(BillingContract.CONTRACT_NAME, billingState)
                command(someoneElsesParty.owningKey, BillingContract.Commands.UseChip(someoneElsesParty))
                failsWith("There should be a UseChip command for each BillingChip owner")
            }
            transaction {
                val billingState = billingState()
                billingState.chipOff(1L)
                input(BillingContract.CONTRACT_NAME, billingState.chipOff(1L).second)
                command(ownerParty.owningKey, BillingContract.Commands.UseChip(ownerParty))
                failsWith(" There should be a valid reference BillingState for each BillingChip")
            }
            transaction {
                val billingState = billingState(expiryDate = Instant.now())
                billingState.chipOff(1L)
                input(BillingContract.CONTRACT_NAME, billingState.chipOff(1L).second)
                command(ownerParty.owningKey, BillingContract.Commands.UseChip(ownerParty))
                reference(BillingContract.CONTRACT_NAME, billingState)
                timeWindow(Instant.now())
                failsWith("Output BillingState expiry date should be within the specified time window")
            }

            transaction {
                val billingState = billingState()
                billingState.chipOff(1L)
                input(BillingContract.CONTRACT_NAME, billingState.chipOff(1L).second)
                reference(BillingContract.CONTRACT_NAME, billingState.copy(status = BillingStateStatus.RETURNED))
                command(ownerParty.owningKey, BillingContract.Commands.UseChip(ownerParty))
                failsWith("Reference BillingState status should be ACTIVE")
            }

        }
    }

    @Test
    fun `test attach back`() {
        val inputBillingState = billingState(spent = 10L)
        val outputBillingState = inputBillingState.copy(spent = 8L)
        val inputChipStates = listOf(inputBillingState.chipOff(1L).second, inputBillingState.chipOff(1L).second)
        ledgerServices.ledger {
            // happy path
            transaction {
                input(BillingContract.CONTRACT_NAME, inputBillingState)
                inputChipStates.forEach {
                    input(BillingContract.CONTRACT_NAME, it)
                }
                output(BillingContract.CONTRACT_NAME, outputBillingState)
                command(ownerParty.owningKey, BillingContract.Commands.AttachBack())
                verifies()
            }
            // happy path with time window
            transaction {
                val expiryDate = Instant.now()
                input(BillingContract.CONTRACT_NAME, inputBillingState.copy(expiryDate = expiryDate))
                inputChipStates.forEach {
                    input(BillingContract.CONTRACT_NAME, it)
                }
                output(BillingContract.CONTRACT_NAME, outputBillingState.copy(expiryDate = expiryDate))
                command(ownerParty.owningKey, BillingContract.Commands.AttachBack())
                timeWindow(expiryDate.minusSeconds(50))
                verifies()
            }
            transaction {
                input(BillingContract.CONTRACT_NAME, inputBillingState)
                input(BillingContract.CONTRACT_NAME, inputBillingState)
                inputChipStates.forEach {
                    input(BillingContract.CONTRACT_NAME, it)
                }
                output(BillingContract.CONTRACT_NAME, outputBillingState)
                command(ownerParty.owningKey, BillingContract.Commands.AttachBack())
                failsWith("Should have one input of BillingState type")
            }
            transaction {
                input(BillingContract.CONTRACT_NAME, inputBillingState)
                output(BillingContract.CONTRACT_NAME, outputBillingState)
                command(ownerParty.owningKey, BillingContract.Commands.AttachBack())
                failsWith("Should have at least one input of BillingChipState type")
            }
            transaction {
                input(BillingContract.CONTRACT_NAME, inputBillingState)
                inputChipStates.forEach {
                    input(BillingContract.CONTRACT_NAME, it)
                }
                command(ownerParty.owningKey, BillingContract.Commands.AttachBack())
                failsWith("Should have a single output of BillingState type")
            }
            transaction {
                input(BillingContract.CONTRACT_NAME, inputBillingState)
                inputChipStates.forEach {
                    input(BillingContract.CONTRACT_NAME, it)
                }
                output(BillingContract.CONTRACT_NAME, outputBillingState.copy(issued = outputBillingState.issued + 1))
                command(ownerParty.owningKey, BillingContract.Commands.AttachBack())
                failsWith("Input and output BillingStates should be equal except the `spent` field")
            }
            transaction {
                input(BillingContract.CONTRACT_NAME, inputBillingState)
                inputChipStates.forEach {
                    input(BillingContract.CONTRACT_NAME, it)
                }
                output(BillingContract.CONTRACT_NAME, outputBillingState.copy(spent = -1L))
                command(ownerParty.owningKey, BillingContract.Commands.AttachBack())
                failsWith("Spent amount of the output BillingState should be not negative")
            }
            transaction {
                input(BillingContract.CONTRACT_NAME, inputBillingState)
                inputChipStates.forEach {
                    input(BillingContract.CONTRACT_NAME, it)
                }
                output(BillingContract.CONTRACT_NAME, outputBillingState)
                command(listOf(ownerParty.owningKey, issuerParty.owningKey), BillingContract.Commands.AttachBack())
                failsWith("AttachBack transaction should be signed only by the owner")
            }
            transaction {
                input(BillingContract.CONTRACT_NAME, inputBillingState)
                inputChipStates.forEach {
                    input(BillingContract.CONTRACT_NAME, it.copy(billingStateLinearId = UniqueIdentifier()))
                }
                output(BillingContract.CONTRACT_NAME, outputBillingState)
                command(ownerParty.owningKey, BillingContract.Commands.AttachBack())
                failsWith("BillingChipStates should match BillingStates")
            }
            transaction {
                input(BillingContract.CONTRACT_NAME, inputBillingState)
                inputChipStates.forEach {
                    input(BillingContract.CONTRACT_NAME, it.copy(amount = -1L))
                }
                output(BillingContract.CONTRACT_NAME, outputBillingState)
                command(ownerParty.owningKey, BillingContract.Commands.AttachBack())
                failsWith("BillingChipState amount should be positive")
            }
            transaction {
                input(BillingContract.CONTRACT_NAME, inputBillingState)
                inputChipStates.forEach {
                    input(BillingContract.CONTRACT_NAME, it.copy(amount = Long.MAX_VALUE - 1))
                }
                output(BillingContract.CONTRACT_NAME, outputBillingState)
                command(ownerParty.owningKey, BillingContract.Commands.AttachBack())
                failsWith("Total AttachBack value should not exceed Long.MAX_VALUE")
            }
            transaction {
                input(BillingContract.CONTRACT_NAME, inputBillingState)
                inputChipStates.forEach {
                    input(BillingContract.CONTRACT_NAME, it)
                }
                output(BillingContract.CONTRACT_NAME, outputBillingState.copy(spent = outputBillingState.spent + 1L))
                command(ownerParty.owningKey, BillingContract.Commands.AttachBack())
                failsWith("Spent amount of the output BillingState should be decremented on the total of the chip off value")
            }
            transaction {
                val expiryDate = Instant.now()
                input(BillingContract.CONTRACT_NAME, inputBillingState.copy(expiryDate = expiryDate))
                inputChipStates.forEach {
                    input(BillingContract.CONTRACT_NAME, it)
                }
                output(BillingContract.CONTRACT_NAME, outputBillingState.copy(expiryDate = expiryDate))
                command(ownerParty.owningKey, BillingContract.Commands.AttachBack())
                timeWindow(expiryDate)
                failsWith("Output BillingState expiry date should be within the specified time window")
            }
            transaction {
                input(BillingContract.CONTRACT_NAME, inputBillingState.copy(status = BillingStateStatus.RETURNED))
                inputChipStates.forEach {
                    input(BillingContract.CONTRACT_NAME, it)
                }
                output(BillingContract.CONTRACT_NAME, outputBillingState.copy(status = BillingStateStatus.RETURNED))
                command(ownerParty.owningKey, BillingContract.Commands.AttachBack())
                failsWith("BillingState status should be ACTIVE")
            }

        }
    }

    @Test
    fun `test return`() = verifyReturnRevokeClose(
                listOf(BillingStateStatus.ACTIVE),
                BillingStateStatus.RETURNED,
                BillingContract.Commands.Return(),
                listOf(issuerParty, ownerParty))

    @Test
    fun `test revoke`() = verifyReturnRevokeClose(listOf(BillingStateStatus.ACTIVE),
                BillingStateStatus.REVOKED,
                BillingContract.Commands.Revoke(),
                listOf(issuerParty))

    @Test
    fun `test close`() = verifyReturnRevokeClose(listOf(BillingStateStatus.RETURNED, BillingStateStatus.REVOKED),
                BillingStateStatus.CLOSED,
                BillingContract.Commands.Close(),
                listOf(issuerParty))

    private fun verifyReturnRevokeClose(inputStateStatuses : List<BillingStateStatus>,
                                        outputStateStatus : BillingStateStatus,
                                        command : BillingContract.Commands,
                                        signers : List<Party>) {
        val signingKeys = signers.map { it.owningKey }
        val inputBillingState = billingState(expiryDate = Instant.now(), status = inputStateStatuses.first())
        val outputBillingState = inputBillingState.copy(status = outputStateStatus)
        ledgerServices.ledger {
            inputStateStatuses.forEach {
                // happy path
                transaction {
                    input(BillingContract.CONTRACT_NAME, inputBillingState.copy(status = it))
                    output(BillingContract.CONTRACT_NAME, outputBillingState)
                    command(signingKeys, command)
                    verifies()
                }
                // revocations should be accepted past expiry date
                transaction {
                    input(BillingContract.CONTRACT_NAME, inputBillingState)
                    output(BillingContract.CONTRACT_NAME, outputBillingState)
                    timeWindow(Instant.now().minusSeconds(10000))
                    command(signingKeys, command)
                    verifies()
                }
            }
            transaction {
                input(BillingContract.CONTRACT_NAME, inputBillingState)
                output(BillingContract.CONTRACT_NAME, outputBillingState)
                output(BillingContract.CONTRACT_NAME, outputBillingState)
                command(signingKeys, command)
                failsWith("There should be a single output of BillingState type")
            }
            transaction {
                input(BillingContract.CONTRACT_NAME, inputBillingState)
                input(BillingContract.CONTRACT_NAME, inputBillingState)
                output(BillingContract.CONTRACT_NAME, outputBillingState)
                command(signingKeys, command)
                failsWith("There should be a single input of BillingState type")
            }
            transaction {
                input(BillingContract.CONTRACT_NAME, inputBillingState)
                output(BillingContract.CONTRACT_NAME, outputBillingState)
                command(someoneElsesParty.owningKey, command)
                failsWith("$signers should be transaction signers")
            }
            transaction {
                input(BillingContract.CONTRACT_NAME, inputBillingState)
                output(BillingContract.CONTRACT_NAME, outputBillingState.copy(issuer = someoneElsesParty))
                command(signingKeys, command)
                failsWith("Input and output BillingsState should be equal but the `status` field")
            }
            transaction {
                input(BillingContract.CONTRACT_NAME, inputBillingState.copy(status = BillingStateStatus.CLOSED))
                output(BillingContract.CONTRACT_NAME, outputBillingState)
                command(signingKeys, command)
                failsWith("Input BillingsState status should be one of $inputStateStatuses")
            }
            transaction {
                input(BillingContract.CONTRACT_NAME, inputBillingState)
                output(BillingContract.CONTRACT_NAME, outputBillingState.copy(status = BillingStateStatus.ACTIVE))
                command(signingKeys, command)
                failsWith("Output BillingsState status should be $outputStateStatus")
            }
        }
    }


    private fun randomTestIdentity() = TestIdentity(CordaX500Name.parse("O=${RandomStringUtils.randomAlphabetic(10)},L=London,C=GB"))


    private fun generateUseChipTransaction(dsl : TransactionDSL<TransactionDSLInterpreter>,
                                           numberOfOwners: Int = 10,
                                           chipsPerOwner: Int = 3,
                                           addTimeWindow : Boolean = true) {
        val expiryDate = Instant.now()

        dsl.apply {
            (0..numberOfOwners).forEach { _ ->
                val owner = randomTestIdentity()
                // generating a billing state for each owner with a unique issuer
                val issuer = randomTestIdentity()

                val billingStateForOwner = if (addTimeWindow)
                    billingState(issuer = issuer.party, owner = owner.party)
                else
                    billingState(issuer = issuer.party, owner = owner.party, expiryDate = expiryDate)

                // generating and adding 3 billing chips for each billing state
                (0..chipsPerOwner).forEach {
                    input(BillingContract.CONTRACT_NAME, billingStateForOwner.chipOff(RandomUtils.nextInt() % 3L).second)
                }
                // adding billing state as a reference input
                reference(BillingContract.CONTRACT_NAME, billingStateForOwner)
                // adding a command for each owner
                command( owner.party.owningKey, BillingContract.Commands.UseChip(owner.party))
                if (addTimeWindow) {
                    timeWindow(expiryDate.minusSeconds(50))
                }
            }
        }
    }
}