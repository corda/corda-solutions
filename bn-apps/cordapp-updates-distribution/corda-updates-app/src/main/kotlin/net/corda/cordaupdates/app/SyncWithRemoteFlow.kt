package net.corda.cordaupdates.app

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.resolution.VersionRangeResult
import org.eclipse.aether.version.Version

class SyncWithRemoteFlow() : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
//        val resolverService = serviceHub.cordaService(CordaMavenResolverService::class.java)
//        resolverService.downloadVersionRangeAsync(rangeRequest) {
//
//        }
    }
}


data class ArtifactGroupAndName(val group : String, val name : String)


@CordaService
class VersionInfoHolder(val appServiceHub : AppServiceHub) : SingletonSerializeAsToken() {
    private var remoteVersions : VersionRange? = null
    private var localVersions : VersionRange? = null
}

data class VersionRange(val artifact : Artifact, val versions : List<Version>) {
    companion object {
        fun create(rangeResult : VersionRangeResult) : VersionRange = VersionRange(rangeResult.request.artifact, rangeResult.versions.toList())
    }
}