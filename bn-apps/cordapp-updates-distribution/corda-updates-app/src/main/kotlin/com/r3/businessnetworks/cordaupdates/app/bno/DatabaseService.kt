package com.r3.businessnetworks.cordaupdates.app.bno

import com.google.common.collect.ImmutableList
import com.r3.businessnetworks.cordaupdates.app.member.CordappVersionInfo
import net.corda.core.identity.Party
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.SingletonSerializeAsToken
import java.io.Serializable
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Table
import javax.persistence.UniqueConstraint

class CordappVersionInfoSchema
class CordappVersionInfoSchemaV1 internal constructor() : MappedSchema(CordappVersionInfoSchema::class.java, 1, ImmutableList.of(PersistentCordappVersionInfo::class.java))

@Entity(name = "PersistentCordappVersionInfo")
@Table(name = "cordapp_version_info", uniqueConstraints = [UniqueConstraint(columnNames = ["party", "cordapp_group", "cordapp_name"])])
class PersistentCordappVersionInfo : Serializable {
    companion object {
        fun from(party : Party, cordappVersionInfo : CordappVersionInfo) : PersistentCordappVersionInfo {
            val dbObject = PersistentCordappVersionInfo()
            dbObject.party = party.toString()
            dbObject.cordappGroup = cordappVersionInfo.group
            dbObject.cordappName = cordappVersionInfo.name
            dbObject.cordappVersion = cordappVersionInfo.version
            dbObject.lastUpdated = cordappVersionInfo.updated
            return dbObject
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    var id : Long = 0

    @Column(name = "party", nullable = false)
    var party : String? = null

    @Column(name = "cordapp_group", nullable = false)
    var cordappGroup : String? = null

    @Column(name = "cordapp_name", nullable = false)
    var cordappName : String? = null

    @Column(name = "cordapp_version", nullable = false)
    var cordappVersion : String? = null

    @Column(name = "last_updated", nullable = false)
    var lastUpdated : Long? = null

    fun toCordappVersionInfo() : CordappVersionInfo {
        return CordappVersionInfo(cordappGroup!!, cordappName!!, cordappVersion!!, lastUpdated!!)
    }
}

/**
 * Allows BNO to store and to retrieve reported CorDapp versions from the database
 */
@CordaService
class DatabaseService(private val serviceHub : AppServiceHub) : SingletonSerializeAsToken() {
    /**
     * Updates or inserts information about a cordapp version
     */
    fun updateCordappVersionInfo(party : Party, cordappVersionInfo : CordappVersionInfo) {
        // trying update first
        val updated : Int = tryUpdateCordappVersionInfo(party, cordappVersionInfo)

        // if nothing has been updated then insert
        if (updated == 0) insertCordappVersionInfo(party, cordappVersionInfo)
    }

    private fun tryUpdateCordappVersionInfo(party : Party, cordappVersionInfo : CordappVersionInfo) : Int {
        return serviceHub.withEntityManager {
            val hqlUpdateQuery = """
                update PersistentCordappVersionInfo
                set cordappVersion = :cordappVersion, lastUpdated = :lastUpdated
                where party = :party AND cordappGroup = :cordappGroup AND cordappName = :cordappName
            """
            val query = createQuery(hqlUpdateQuery)
            query.setParameter("cordappVersion", cordappVersionInfo.version)
                    .setParameter("lastUpdated", System.currentTimeMillis())
                    .setParameter("party", party.name.toString())
                    .setParameter("cordappGroup", cordappVersionInfo.group)
                    .setParameter("cordappName", cordappVersionInfo.name)
                    .executeUpdate()
        }
    }

    private fun insertCordappVersionInfo(party : Party, cordappVersionInfo : CordappVersionInfo) {
        serviceHub.withEntityManager {
            persist(PersistentCordappVersionInfo.from(party, cordappVersionInfo))
        }
    }

    /**
     * Get information about all reported cordapps versions
     */
    fun getCordappVersionInfos() : Map<String, List<CordappVersionInfo>> {
        val results = mapOf<String, MutableList<CordappVersionInfo>>()

        serviceHub.withEntityManager {
            createQuery("from PersistentCordappVersionInfo")
                    .resultList
                    .map {
                        val persistentCordappVersionInfo = it as PersistentCordappVersionInfo
                        val versions : MutableList<CordappVersionInfo> = results.getOrElse(persistentCordappVersionInfo.party.toString()) { mutableListOf() }
                        versions.add(it.toCordappVersionInfo())
                    }
        }

        return results
    }

    /**
     * Get information about reported cordapps versions for the provided party
     */
    fun getCordappVersionInfos(party : Party) : List<CordappVersionInfo> {
        return serviceHub.withEntityManager {
            val hqlQuery = """
                from PersistentCordappVersionInfo where party=:party
            """

            createQuery(hqlQuery)
                    .setParameter("party", party.toString())
                    .resultList.map {
                (it as PersistentCordappVersionInfo).toCordappVersionInfo()
            }
        }
    }
}