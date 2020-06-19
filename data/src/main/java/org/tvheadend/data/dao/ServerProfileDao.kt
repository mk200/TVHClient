package org.tvheadend.data.dao

import androidx.room.*
import org.tvheadend.data.entity.ServerProfileEntity

@Dao
interface ServerProfileDao {

    @get:Query("SELECT COUNT (*) FROM server_profiles AS p " +
            " WHERE $CONNECTION_IS_ACTIVE")
    val itemCountSync: Int

    @Query("SELECT p.* FROM server_profiles AS p " +
            " WHERE $CONNECTION_IS_ACTIVE" +
            " AND p.type = 'htsp_playback'")
    fun loadHtspPlaybackProfilesSync(): List<ServerProfileEntity>

    @Query("SELECT p.* FROM server_profiles AS p " +
            " WHERE $CONNECTION_IS_ACTIVE" +
            " AND p.type = 'http_playback'")
    fun loadHttpPlaybackProfilesSync(): List<ServerProfileEntity>

    @Query("SELECT p.* FROM server_profiles AS p " +
            " WHERE $CONNECTION_IS_ACTIVE" +
            " AND p.type = 'recording'")
    fun loadAllRecordingProfilesSync(): List<ServerProfileEntity>

    @Insert
    fun insert(serverProfile: ServerProfileEntity)

    @Update
    fun update(serverProfile: ServerProfileEntity)

    @Delete
    fun delete(serverProfile: ServerProfileEntity)

    @Query("DELETE FROM server_profiles")
    fun deleteAll()

    @Query("SELECT p.* FROM server_profiles AS p " +
            " WHERE $CONNECTION_IS_ACTIVE" +
            " AND p.id = :id")
    fun loadProfileByIdSync(id: Int): ServerProfileEntity?

    @Query("SELECT p.* FROM server_profiles AS p " +
            " WHERE $CONNECTION_IS_ACTIVE" +
            " AND p.uuid = :uuid")
    fun loadProfileByUuidSync(uuid: String): ServerProfileEntity?

    companion object {

        const val CONNECTION_IS_ACTIVE = " p.connection_id IN (SELECT id FROM connections WHERE active = 1) "
    }
}
