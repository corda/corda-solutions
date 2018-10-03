package net.corda.cordaupdates.app.bno

import net.corda.cordaupdates.app.member.CordappVersionInfo
import net.corda.core.identity.Party
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken

@CordaService
class DatabaseService(private val serviceHub : AppServiceHub) : SingletonSerializeAsToken() {
    companion object {
        // the table with pending membership request. See RequestMembershipFlow for more details
        const val CORDAPP_VERSION_INFO_TABLE = "cordapp_version_info"
        const val PARTY = "party"
        const val CORDAPP_GROUP = "cordapp_group"
        const val CORDAPP_NAME = "cordapp_name"
        const val CORDAPP_VERSION = "cordapp_version"
        const val LAST_UPDATED = "last_updated"

    }

    init {
        // Create table if it doesn't already exist.
        val nativeQuery = """
                create table if not exists $CORDAPP_VERSION_INFO_TABLE (
                    $PARTY varchar(255) not null,
                    $CORDAPP_GROUP varchar(255) not null,
                    $CORDAPP_NAME varchar(255) not null,
                    $CORDAPP_VERSION varchar(20) not null,
                    $LAST_UPDATED bigint not null
                );
                create unique index if not exists party_group_name_unique_index on $CORDAPP_VERSION_INFO_TABLE($PARTY, $CORDAPP_GROUP, $CORDAPP_NAME);
            """
        val session = serviceHub.jdbcSession()
        session.prepareStatement(nativeQuery).execute()
    }

    fun updateCordappVersionInfo(party : Party, cordappVersionInfo : CordappVersionInfo) {
        val insertQuery = """
        insert into $CORDAPP_VERSION_INFO_TABLE ($PARTY, $CORDAPP_GROUP, $CORDAPP_NAME, $CORDAPP_VERSION, $LAST_UPDATED)
        values ('${party.name}', '${cordappVersionInfo.group}', '${cordappVersionInfo.name}', '${cordappVersionInfo.version}', ${System.currentTimeMillis()})
        """
        val updateQuery = """
        update $CORDAPP_VERSION_INFO_TABLE
        set $CORDAPP_VERSION = '${cordappVersionInfo.version}', $LAST_UPDATED = ${System.currentTimeMillis()}
        where $PARTY = '${party.name}' AND $CORDAPP_GROUP = '${cordappVersionInfo.group}' AND $CORDAPP_NAME = '${cordappVersionInfo.name}'
        """

        val session = serviceHub.jdbcSession()

        // trying update first
        val updated = session.prepareStatement(updateQuery).use { it.executeUpdate() }
        // if nothing has been updated then insert
        if (updated == 0) session.prepareStatement(insertQuery).use { it.executeUpdate() }
    }

    fun getCordappVersionInfos() : Map<String, List<CordappVersionInfo>> {
        val nativeQuery = """
                select * from $CORDAPP_VERSION_INFO_TABLE
            """
        val session = serviceHub.jdbcSession()
        val resultSet = session.prepareStatement(nativeQuery).use { it.executeQuery()!! }
        val results = mapOf<String, MutableList<CordappVersionInfo>>()
        while (resultSet.next()) {
            val party = resultSet.getString(PARTY)!!
            val versions = results.getOrElse(party) { mutableListOf() }
            versions.add(
                    CordappVersionInfo(resultSet.getString(CORDAPP_GROUP)!!,
                            resultSet.getString(CORDAPP_NAME)!!,
                            resultSet.getString(CORDAPP_VERSION)!!,
                            resultSet.getLong(LAST_UPDATED)))
        }
        return results
    }

    fun getCordappVersionInfos(party : Party) : List<CordappVersionInfo> {
        val nativeQuery = """
                select * from $CORDAPP_VERSION_INFO_TABLE where $PARTY='${party.name}'
            """
        val session = serviceHub.jdbcSession()
        return session.prepareStatement(nativeQuery).use {
            val resultSet = it.executeQuery()!!
            val results = mutableListOf<CordappVersionInfo>()
            while (resultSet.next()) {
                results.add(
                        CordappVersionInfo(resultSet.getString(CORDAPP_GROUP)!!,
                                resultSet.getString(CORDAPP_NAME)!!,
                                resultSet.getString(CORDAPP_VERSION)!!,
                                resultSet.getLong(LAST_UPDATED)))
            }
            results
        }
    }
}