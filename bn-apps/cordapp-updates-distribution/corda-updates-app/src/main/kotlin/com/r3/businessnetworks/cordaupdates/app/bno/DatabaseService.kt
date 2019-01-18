package com.r3.businessnetworks.cordaupdates.app.bno

import com.r3.businessnetworks.cordaupdates.app.member.CordappVersionInfo
import net.corda.core.identity.Party
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * Allows BNO to store and to retrieve reported CorDapp versions from the database
 */
@CordaService
class DatabaseService(private val serviceHub : AppServiceHub) : SingletonSerializeAsToken() {
    companion object {
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
        val session : Connection = serviceHub.jdbcSession()
        session.prepareStatement(nativeQuery).execute()
    }

    /**
     * Updates or inserts information about a cordapp version
     */
    fun updateCordappVersionInfo(party : Party, cordappVersionInfo : CordappVersionInfo) {
        val session : Connection = serviceHub.jdbcSession()

        // trying update first
        val updated : Int = tryUpdateCordappVersionInfo(session, party, cordappVersionInfo)

        // if nothing has been updated then insert
        if (updated == 0) insertCordappVersionInfo(session, party, cordappVersionInfo)
    }

    private fun tryUpdateCordappVersionInfo(session : Connection, party : Party, cordappVersionInfo : CordappVersionInfo) : Int {
        val updateQuery = """
            update $CORDAPP_VERSION_INFO_TABLE
            set $CORDAPP_VERSION = ?, $LAST_UPDATED = ?
            where $PARTY = ? AND $CORDAPP_GROUP = ? AND $CORDAPP_NAME = ?
            """

        val updateStatement : PreparedStatement = session.prepareStatement(updateQuery)

        updateStatement.setString(1, cordappVersionInfo.version)
        updateStatement.setLong(2, System.currentTimeMillis())
        updateStatement.setString(3, party.name.toString())
        updateStatement.setString(4, cordappVersionInfo.group)
        updateStatement.setString(5, cordappVersionInfo.name)

        return updateStatement.executeUpdate()
    }

    private fun insertCordappVersionInfo(session : Connection, party : Party, cordappVersionInfo : CordappVersionInfo) {
        val insertQuery = """
                insert into $CORDAPP_VERSION_INFO_TABLE ($PARTY, $CORDAPP_GROUP, $CORDAPP_NAME, $CORDAPP_VERSION, $LAST_UPDATED)
                values (?, ?, ?, ?, ?)
                """
        val insertStatement : PreparedStatement = session.prepareStatement(insertQuery)
        insertStatement.setString(1, party.name.toString())
        insertStatement.setString(2, cordappVersionInfo.group)
        insertStatement.setString(3, cordappVersionInfo.name)
        insertStatement.setString(4, cordappVersionInfo.version)
        insertStatement.setLong(5, System.currentTimeMillis())
        insertStatement.executeUpdate()
    }

    /**
     * Get information about all reported cordapps versions
     */
    fun getCordappVersionInfos() : Map<String, List<CordappVersionInfo>> {
        val nativeQuery = """
                select * from $CORDAPP_VERSION_INFO_TABLE
            """
        val session : Connection = serviceHub.jdbcSession()
        val resultSet : ResultSet = session.prepareStatement(nativeQuery).use { it.executeQuery()!! }
        val results = mapOf<String, MutableList<CordappVersionInfo>>()
        while (resultSet.next()) {
            val party : String = resultSet.getString(PARTY)!!
            val versions : MutableList<CordappVersionInfo> = results.getOrElse(party) { mutableListOf() }
            versions.add(
                    CordappVersionInfo(resultSet.getString(CORDAPP_GROUP)!!,
                            resultSet.getString(CORDAPP_NAME)!!,
                            resultSet.getString(CORDAPP_VERSION)!!,
                            resultSet.getLong(LAST_UPDATED)))
        }
        return results
    }

    /**
     * Get information about reported cordapps versions for the provided party
     */
    fun getCordappVersionInfos(party : Party) : List<CordappVersionInfo> {
        val nativeQuery = """
                select * from $CORDAPP_VERSION_INFO_TABLE where $PARTY='${party.name}'
            """
        val session : Connection = serviceHub.jdbcSession()
        return session.prepareStatement(nativeQuery).use {
            val resultSet : ResultSet = it.executeQuery()!!
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