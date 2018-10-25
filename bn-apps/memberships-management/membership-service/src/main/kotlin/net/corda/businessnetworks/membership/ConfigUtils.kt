package net.corda.businessnetworks.membership

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.core.internal.div
import java.io.File
import java.nio.file.Paths

/**
 * Attempts to load application config from cordapps/config/membership-service.conf with fallback to membership-service.conf on the classpath
 */
object ConfigUtils {
    fun loadConfig() : Config {
        val propertiesFileName = "membership-service.conf"
        val defaultLocation = (Paths.get("cordapps") / "config" / propertiesFileName).toFile()
        return if (defaultLocation.exists()) ConfigFactory.parseFile(defaultLocation)
            else ConfigFactory.parseFile(File(ConfigUtils::class.java.classLoader.getResource(propertiesFileName).toURI()))
    }
}