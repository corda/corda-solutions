package net.corda.businessnetworks.membership.states

import net.corda.core.contracts.Contract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test
import java.time.Instant

class MembershipContractTest {
    private var ledgerServices = MockServices(listOf("net.corda.businessnetworks.membership.states"))
    private val member = TestIdentity(CordaX500Name.parse("O=Member,L=London,C=GB"))
    private val anotherMember = TestIdentity(CordaX500Name.parse("O=Another Member,L=New York,C=US"))
    private val bno = TestIdentity(CordaX500Name.parse("O=BNO,L=London,C=GB"))
    private val memberParty = member.party
    private val anotherMemberParty = anotherMember.party
    private val bnoParty = bno.party

    private fun membershipState(status : MembershipStatus = MembershipStatus.PENDING, member : Party = memberParty, bno : Party = bnoParty, issued : Instant = Instant.now()) = Membership.State(member, bno, status = status)


    @Test
    fun `test common assertions`() {
        ledgerServices.ledger {
            val output = membershipState()
            transaction {
                output(Membership.CONTRACT_NAME,  output.copy(modified = Instant.now().minusSeconds(100)))
                command(listOf(member.publicKey), Membership.Commands.Request())
                this.`fails with`("Modified date has to be greater or equal than the issued date")
            }
            transaction {
                val input = output.copy(member = anotherMemberParty)
                input(Membership.CONTRACT_NAME,  input)
                output(Membership.CONTRACT_NAME,  output)
                command(listOf(member.publicKey), Membership.Commands.Request())
                this.`fails with`("Participants of input and output states should be the same")
            }
            transaction {
                val input = output.copy()
                input(MyDummyContract.CONTRACT_NAME,  input)
                output(Membership.CONTRACT_NAME, output )
                command(listOf(member.publicKey), Membership.Commands.Request())
                this.`fails with`("Input state has to be validated with ${Membership.CONTRACT_NAME}")
            }
            transaction {
                val wrongIssuedDate = Instant.now().minusSeconds(100)
                val input = output.copy(issued = wrongIssuedDate, modified = wrongIssuedDate)
                input(Membership.CONTRACT_NAME, input)
                output(Membership.CONTRACT_NAME, output )
                command(listOf(member.publicKey), Membership.Commands.Request())
                this.`fails with`("Input and output states should have the same issued dates")
            }
            transaction {
                val input = output.copy(linearId = UniqueIdentifier())
                input(Membership.CONTRACT_NAME, input)
                output(Membership.CONTRACT_NAME, output)
                command(listOf(member.publicKey), Membership.Commands.Request())
                this.`fails with`("Input and output states should have the same linear ids")
            }
            transaction {
                val issuedDate = Instant.now()
                val inputModifiedDate = issuedDate.plusSeconds(10)
                val outputModifiedDate = issuedDate.plusSeconds(5)
                input(Membership.CONTRACT_NAME, output.copy(issued = issuedDate, modified = inputModifiedDate))
                output(Membership.CONTRACT_NAME, output.copy(issued = issuedDate, modified = outputModifiedDate))
                command(listOf(member.publicKey), Membership.Commands.Request())
                this.`fails with`("Output state's modified timestamp should be greater than input's")
            }
        }
    }

    @Test
    fun `test request membership`() {
        ledgerServices.ledger {
            transaction {
                output(Membership.CONTRACT_NAME,  membershipState())
                command(listOf(member.publicKey, bno.publicKey), Membership.Commands.Request())
                this.verifies()
            }
            transaction {
                output(Membership.CONTRACT_NAME,  membershipState())
                command(listOf(member.publicKey), Membership.Commands.Request())
                this.`fails with`("Both BNO and member have to sign a membership request transaction")
            }
            transaction {
                output(Membership.CONTRACT_NAME,  membershipState(MembershipStatus.REVOKED))
                command(listOf(member.publicKey, bno.publicKey), Membership.Commands.Request())
                this.`fails with`("Membership request transaction should contain an output state in PENDING status")
            }
            transaction {
                val state  = membershipState()
                input(Membership.CONTRACT_NAME,  state)
                output(Membership.CONTRACT_NAME,  state.copy(modified = state.modified.plusSeconds(10)))
                command(listOf(member.publicKey, bno.publicKey), Membership.Commands.Request())
                this.`fails with`("Membership request transaction shouldn't contain any inputs")
            }
        }
    }

    @Test
    fun `test revoke membership`() {
        ledgerServices.ledger {
            transaction {
                val input = membershipState(MembershipStatus.ACTIVE)
                input(Membership.CONTRACT_NAME,  input)
                output(Membership.CONTRACT_NAME,  input.copy(status = MembershipStatus.REVOKED, modified = input.modified.plusMillis(1)))
                command(listOf(bno.publicKey), Membership.Commands.Revoke())
                this.verifies()
            }
            transaction {
                val input = membershipState(MembershipStatus.ACTIVE)
                input(Membership.CONTRACT_NAME,  input)
                output(Membership.CONTRACT_NAME,  input.copy(status = MembershipStatus.REVOKED, modified = input.modified.plusMillis(1)))
                command(listOf(bno.publicKey, member.publicKey), Membership.Commands.Revoke())
                this.`fails with`("Only BNO should sign a revocation transaction")
            }
            transaction {
                val input = membershipState(MembershipStatus.REVOKED)
                input(Membership.CONTRACT_NAME,  input)
                output(Membership.CONTRACT_NAME,  input.copy(status = MembershipStatus.REVOKED, modified = input.modified.plusMillis(1)))
                command(listOf(bno.publicKey), Membership.Commands.Revoke())
                this.`fails with`("Input state of a revocation transaction shouldn't be already revoked")
            }
            transaction {
                val input = membershipState(MembershipStatus.ACTIVE)
                input(Membership.CONTRACT_NAME,  input)
                output(Membership.CONTRACT_NAME,  input.copy(status = MembershipStatus.PENDING, modified = input.modified.plusMillis(1)))
                command(listOf(bno.publicKey), Membership.Commands.Revoke())
                this.`fails with`( "Output state of a revocation transaction should be revoked")
            }
            transaction {
                val input = membershipState(MembershipStatus.ACTIVE)
                input(Membership.CONTRACT_NAME,  input)
                output(Membership.CONTRACT_NAME,  input.copy(status = MembershipStatus.REVOKED, modified = input.modified.plusMillis(1), membershipMetadata = MembershipMetadata("Another role")))
                command(listOf(bno.publicKey), Membership.Commands.Revoke())
                this.`fails with`("Input and output states of a revocation transaction should have the same metadata")
            }
        }
    }

    @Test
    fun `test activate membership`() {
        ledgerServices.ledger {
            transaction {
                val input = membershipState(MembershipStatus.REVOKED)
                input(Membership.CONTRACT_NAME,  input)
                output(Membership.CONTRACT_NAME,  input.copy(status = MembershipStatus.ACTIVE, modified = input.modified.plusMillis(1)))
                command(listOf(bno.publicKey), Membership.Commands.Activate())
                this.verifies()
            }
            transaction {
                val input = membershipState(MembershipStatus.REVOKED)
                input(Membership.CONTRACT_NAME,  input)
                output(Membership.CONTRACT_NAME,  input.copy(status = MembershipStatus.ACTIVE, modified = input.modified.plusMillis(1)))
                command(listOf(bno.publicKey, member.publicKey), Membership.Commands.Activate())
                this.`fails with`("Only BNO should sign a membership activation transaction")
            }
            transaction {
                val input = membershipState(MembershipStatus.ACTIVE)
                input(Membership.CONTRACT_NAME,  input)
                output(Membership.CONTRACT_NAME,  input.copy(status = MembershipStatus.ACTIVE, modified = input.modified.plusMillis(1)))
                command(listOf(bno.publicKey), Membership.Commands.Activate())
                this.`fails with`("Input state of a membership activation transaction shouldn't be already active")
            }
            transaction {
                val input = membershipState(MembershipStatus.REVOKED)
                input(Membership.CONTRACT_NAME,  input)
                output(Membership.CONTRACT_NAME,  input.copy(status = MembershipStatus.PENDING, modified = input.modified.plusMillis(1)))
                command(listOf(bno.publicKey), Membership.Commands.Activate())
                this.`fails with`("Output state of a membership activation transaction should be active")
            }
            transaction {
                val input = membershipState(MembershipStatus.REVOKED)
                input(Membership.CONTRACT_NAME,  input)
                output(Membership.CONTRACT_NAME,  input.copy(status = MembershipStatus.ACTIVE, modified = input.modified.plusMillis(1), membershipMetadata = MembershipMetadata("Another metadata")))
                command(listOf(bno.publicKey), Membership.Commands.Activate())
                this.`fails with`("Input and output states of a membership activation transaction should have the same metadata")
            }
        }
    }


    @Test
    fun `test amend member's metadata`() {
        val input = membershipState(MembershipStatus.ACTIVE)
        val output = input.copy(status = MembershipStatus.ACTIVE, modified = input.modified.plusMillis(1), membershipMetadata = MembershipMetadata("New metadata"))
        ledgerServices.ledger {
            transaction {
                input(Membership.CONTRACT_NAME,  input)
                output(Membership.CONTRACT_NAME,  output)
                command(listOf(bno.publicKey, member.publicKey), Membership.Commands.Amend())
                this.verifies()
            }
            transaction {
                input(Membership.CONTRACT_NAME,  input)
                output(Membership.CONTRACT_NAME,  output)
                command(listOf(bno.publicKey), Membership.Commands.Amend())
                this.`fails with`("Both BNO and member have to sign a metadata amendment transaction")
            }
            transaction {
                input(Membership.CONTRACT_NAME,  input.copy(status = MembershipStatus.PENDING))
                output(Membership.CONTRACT_NAME,  output)
                command(listOf(bno.publicKey, member.publicKey), Membership.Commands.Amend())
                this.`fails with`("Both input and output states of a metadata amendment transaction should be active")
            }
            transaction {
                input(Membership.CONTRACT_NAME,  input)
                output(Membership.CONTRACT_NAME,  output.copy(status = MembershipStatus.PENDING))
                command(listOf(bno.publicKey, member.publicKey), Membership.Commands.Amend())
                this.`fails with`("Both input and output states of a metadata amendment transaction should be active")
            }
            transaction {
                input(Membership.CONTRACT_NAME,  input)
                output(Membership.CONTRACT_NAME,  output.copy(membershipMetadata = input.membershipMetadata))
                command(listOf(bno.publicKey, member.publicKey), Membership.Commands.Amend())
                this.`fails with`("Input and output states of an amendment transaction should have different membership metadata")
            }
        }
    }
}

class MyDummyContract : Contract {
    companion object {
        const val CONTRACT_NAME = "net.corda.businessnetworks.membership.states.MyDummyContract"
    }

    override fun verify(tx : LedgerTransaction) {
    }
}