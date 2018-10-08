package net.corda.businessnetworks.cordaupdates.shell

import org.eclipse.aether.AbstractRepositoryListener
import org.eclipse.aether.RepositoryEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Simple maven resolver repository listener that logs everything into the console
 * The implementation is kindly borrowed from https://github.com/eclipse/aether-demo/blob/master/aether-demo-snippets/src/main/java/org/eclipse/aether/examples/util/ConsoleRepositoryListener.java
 */
class ConsoleRepositoryListener : AbstractRepositoryListener() {
    companion object {
        val logger : Logger by lazy { LoggerFactory.getLogger("corda-updates") }
    }

    override fun artifactDeployed(event : RepositoryEvent) =
            logger.info("Deployed ${event.artifact} to ${event.repository}")

    override fun artifactDeploying(event : RepositoryEvent) =
            logger.info("Deploying ${event.artifact} to ${event.repository}")

    override fun artifactDescriptorInvalid(event : RepositoryEvent) =
            logger.info("Invalid artifact descriptor for ${event.artifact}: ${event.exception.message}")

    override fun artifactDescriptorMissing(event : RepositoryEvent) =
            logger.info("Missing artifact descriptor for ${event.artifact}")

    override fun artifactInstalled(event : RepositoryEvent) =
            logger.info("Installed ${event.artifact} to ${event.file}")

    override fun artifactInstalling(event : RepositoryEvent) =
            logger.info("Installing ${event.artifact} to ${event.file}")

    override fun artifactResolved(event : RepositoryEvent) =
            logger.info("Resolved artifact ${event.artifact} from ${event.repository}")

    override fun artifactDownloading(event : RepositoryEvent) =
            logger.info("Downloading artifact ${event.artifact} from ${event.repository}")

    override fun artifactDownloaded(event : RepositoryEvent) =
            logger.info("Downloaded artifact ${event.artifact} from ${event.repository}")

    override fun artifactResolving(event : RepositoryEvent) =
            logger.info("Resolving artifact ${event.artifact}")

    override fun metadataDeployed(event : RepositoryEvent) =
            logger.info("Deployed ${event.metadata} to ${event.repository}")

    override fun metadataDeploying(event : RepositoryEvent) =
            logger.info("Deploying ${event.metadata} to ${event.repository}")

    override fun metadataInstalled(event : RepositoryEvent) =
            logger.info("Installed ${event.metadata} to ${event.file}")

    override fun metadataInstalling(event : RepositoryEvent) =
            logger.info("Installing ${event.metadata} to ${event.file}")

    override fun metadataInvalid(event : RepositoryEvent) =
            logger.info("Invalid metadata ${event.metadata}")

    override fun metadataResolved(event : RepositoryEvent) =
            logger.info("Resolved metadata ${event.metadata} from ${event.repository}")

    override fun metadataResolving(event : RepositoryEvent) =
            logger.info("Resolving metadata ${event.metadata} from ${event.repository}")
}