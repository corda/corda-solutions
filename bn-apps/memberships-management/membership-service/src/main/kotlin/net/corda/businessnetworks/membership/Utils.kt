package net.corda.businessnetworks.membership

import java.util.*

object Utils {
    fun readProps(fileName : String) : Map<String, String> {
        val input = Utils::class.java.classLoader.getResourceAsStream(fileName)
        val props = Properties()
        props.load(input)
        return props.propertyNames().toList().map { it as String }.map { it to props.getProperty(it)!!}.toMap()
    }
}