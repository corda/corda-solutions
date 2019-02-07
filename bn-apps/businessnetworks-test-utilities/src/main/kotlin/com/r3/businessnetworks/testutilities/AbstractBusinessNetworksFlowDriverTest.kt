package com.r3.businessnetworks.testutilities

import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.TestCordapp

/**
 * Instantiates [numberOfBusinessNetworks] Business Networks and creates [numberOfParticipants] participant nodes using DriverDSL.
 * Sets up a notary with [notaryName] name. Registers [bnoFlowOverrides] flow overrides for BNO nodes and [participantFlowOverrides] flow overrides
 * for participant nodes.
 */
abstract class AbstractBusinessNetworksFlowDriverTest(private val numberOfBusinessNetworks : Int,
                                                      private val numberOfParticipants : Int,
                                                      private val cordappPackages : List<String>,
                                                      private val notaryName : String = "O=Notary,L=London,C=GB",
                                                      private val bnoFlowOverrides : Map<out Class<out FlowLogic<*>>, Class<out FlowLogic<*>>> = mapOf(),
                                                      private val participantFlowOverrides: Map<out Class<out FlowLogic<*>>, Class<out FlowLogic<*>>> = mapOf()) {
    lateinit var bnoNodes : List<NodeHandle>
    lateinit var participantsNodes : List<NodeHandle>

    protected fun runTest(numberOfBusinessNetworks : Int? = null,
                          numberOfParticipants : Int? = null,
                          cordappPackages : List<String>? = null,
                          notaryName : String? = null,
                          bnoFlowOverrides : Map<out Class<out FlowLogic<*>>, Class<out FlowLogic<*>>>? = null,
                          participantFlowOverrides: Map<out Class<out FlowLogic<*>>, Class<out FlowLogic<*>>>? = null,
                          testFunction: () -> Unit) {
        driver(DriverParameters(
                cordappsForAllNodes = (cordappPackages ?: this.cordappPackages).map { TestCordapp.findCordapp(it) },
                startNodesInProcess = true,
                notarySpecs = listOf(NotarySpec(CordaX500Name.parse((notaryName ?: (notaryName ?: this.notaryName)))))
        )) {
            bnoNodes = (1..(numberOfBusinessNetworks ?: this@AbstractBusinessNetworksFlowDriverTest.numberOfBusinessNetworks)).mapIndexed { indexOfBN, _ ->
                val bnoName =  CordaX500Name.parse("O=BNO_$indexOfBN,L=New York,C=US")
                val bnoNode = startNode(NodeParameters(providedName = bnoName, flowOverrides = (bnoFlowOverrides ?: this@AbstractBusinessNetworksFlowDriverTest.bnoFlowOverrides))).getOrThrow()
                bnoNode
            }

            participantsNodes = (1..(numberOfParticipants ?: this@AbstractBusinessNetworksFlowDriverTest.numberOfParticipants)).map { indexOfParticipant ->
                val node = startNode(NodeParameters(providedName = CordaX500Name.parse("O=Participant $indexOfParticipant,L=London,C=GB"),
                        flowOverrides = (participantFlowOverrides ?: this@AbstractBusinessNetworksFlowDriverTest.participantFlowOverrides))).getOrThrow()
                node
            }

            testFunction()
        }
    }

    protected fun <T> runFlowAndReturn(node: NodeHandle, flowClazz: Class<out FlowLogic<T>>, vararg args: Any?): T {
        return node.rpc.startFlowDynamic(flowClazz, *args).returnValue.getOrThrow()
    }
}

fun NodeHandle.identity() = this.nodeInfo.legalIdentities.single()
fun List<NodeHandle>.identities() = map { it.identity() }