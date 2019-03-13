package com.r3.businessnetworks.vaultrecycler

import com.r3.businessnetworks.testutilities.AbstractBusinessNetworksFlowTest
import com.r3.businessnetworks.testutilities.identity
import com.r3.businessnetworks.vaultrecycler.aux.IssueSimpleStateFlow
import com.r3.businessnetworks.vaultrecycler.aux.SimpleState
import net.corda.core.node.services.queryBy
import net.corda.testing.node.StartedMockNode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FindGarbageFlowTest  : AbstractBusinessNetworksFlowTest(
        1, 2, listOf("com.r3.businessnetworks.vaultrecycler")) {

    private val participant1Node : StartedMockNode
        get() = participantsNodes.first()

    @Test
    fun test() {
        // issuing 2 states
        val tx1 = runFlowAndReturn(participant1Node,
                IssueSimpleStateFlow(listOf(),
                        listOf(
                                SimpleState(participant1Node.identity(), "state1"),
                                SimpleState(participant1Node.identity(), "state2")
                        )))
        // no transactions should be garbage collected
        assertTrue(runFlowAndReturn(participant1Node, FindGarbageFlow()).transactions.isEmpty())

        // spending one of the states and issuing 2 more.
        val state1 = participant1Node.stateByTag("state1")
        val tx2 = runFlowAndReturn(participant1Node,
                IssueSimpleStateFlow(listOf(state1), listOf(
                        SimpleState(participant1Node.identity(), "state3"),
                        SimpleState(participant1Node.identity(), "state4")
                )))
        // no transaction should be garbage collected
        assertTrue(runFlowAndReturn(participant1Node, FindGarbageFlow()).transactions.isEmpty())

        // spending state2
        val state2 = participant1Node.stateByTag("state2")
        val tx3 = runFlowAndReturn(participant1Node,
                IssueSimpleStateFlow(listOf(state2), listOf()))
        assertEquals(listOf(tx3.id), runFlowAndReturn(participant1Node, FindGarbageFlow()).transactions)

        // spending state3
        val state3 = participant1Node.stateByTag("state3")
        val tx4 = runFlowAndReturn(participant1Node,
                IssueSimpleStateFlow(listOf(state3), listOf()))
        assertEquals(setOf(tx3.id, tx4.id), runFlowAndReturn(participant1Node, FindGarbageFlow()).transactions.toSet())

        // spending state4
        val state4 = participant1Node.stateByTag("state4")
        val tx5 = runFlowAndReturn(participant1Node,
                IssueSimpleStateFlow(listOf(state4), listOf()))
        // all transactions should be garbage collected
        assertEquals(setOf(tx1.id, tx2.id, tx3.id, tx4.id, tx5.id), runFlowAndReturn(participant1Node, FindGarbageFlow()).transactions.toSet())

        // issuing state5
        val tx6 = runFlowAndReturn(participant1Node,
                IssueSimpleStateFlow(listOf(),
                        listOf(
                                SimpleState(participant1Node.identity(), "state5")
                        )))
        // tx6 should not be garbage collected
        assertEquals(setOf(tx1.id, tx2.id, tx3.id, tx4.id, tx5.id), runFlowAndReturn(participant1Node, FindGarbageFlow()).transactions.toSet())

        // modifying state5. None of the txs involving state6 should be GC'd
        var state5 = participant1Node.stateByTag("state5")
        val tx7 = runFlowAndReturn(participant1Node,
                IssueSimpleStateFlow(listOf(state5),
                        listOf(
                                SimpleState(participant1Node.identity(), "state5")
                        )))
        // tx6 should not be garbage collected
        assertEquals(setOf(tx1.id, tx2.id, tx3.id, tx4.id, tx5.id), runFlowAndReturn(participant1Node, FindGarbageFlow()).transactions.toSet())

        // spending state5. None of the txs involving state6 should be GC'd
        state5 = participant1Node.stateByTag("state5")
        val tx8 = runFlowAndReturn(participant1Node, IssueSimpleStateFlow(listOf(state5), listOf()))
        // not all transactions should be cleaned up
        assertEquals(setOf(tx1.id, tx2.id, tx3.id, tx4.id, tx5.id, tx6.id, tx7.id, tx8.id), runFlowAndReturn(participant1Node, FindGarbageFlow()).transactions.toSet())

    }

    private fun StartedMockNode.stateByTag(tag : String)
            = services.vaultService.queryBy<SimpleState>().states.single { it.state.data.tag == tag }
}