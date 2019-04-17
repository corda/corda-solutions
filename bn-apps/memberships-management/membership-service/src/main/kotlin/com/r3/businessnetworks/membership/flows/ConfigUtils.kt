package com.r3.businessnetworks.membership.flows

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.io.File
import java.net.URL
import java.nio.file.Paths

/**
 * Attempts to load application config from cordapps/config/membership-service.conf with fallback to membership-service.conf on the classpath
 */
object ConfigUtils {
    fun loadConfig() : Config {
        val propertiesFileName = "membership-service.conf"
        val defaultLocation = (Paths.get("cordapps").resolve("config").resolve(propertiesFileName)).toFile()
        return if (defaultLocation.exists()) ConfigFactory.parseFile(defaultLocation)
            else {
                val url = this.javaClass.getResource("./$propertiesFileName")
                    ?: this.javaClass.getResource("/$propertiesFileName")
                ConfigFactory.parseURL(url)
        }
    }
}
