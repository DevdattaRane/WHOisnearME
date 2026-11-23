package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.Friend
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendDao {

    // --- Retrieve ---

    @Query("SELECT * FROM friends ORDER BY isFavorite DESC, COALESCE(NULLIF(nickname, ''), name) COLLATE NOCASE ASC")
    fun getAllFriends(): Flow<List<Friend>>

    @Query("SELECT * FROM friends WHERE id = :id")
    fun observeFriendById(id: Long): Flow<Friend?>

    @Query("SELECT * FROM friends WHERE id = :id")
    suspend fun getFriendById(id: Long): Friend?

    @Query(
        "SELECT * FROM friends WHERE UPPER(deviceIdentifier) = UPPER(:identifier) " +
            "OR UPPER(beaconId) = UPPER(:identifier) " +
            "OR UPPER(macAddress) = UPPER(:identifier) LIMIT 1"
    )
    suspend fun findFriendByDeviceIdentifier(identifier: String): Friend?

    @Query("SELECT * FROM friends")
    suspend fun getAllFriendsSnapshot(): List<Friend>

    // --- Add & Update ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriend(friend: Friend): Long

    @Update
    suspend fun updateFriend(friend: Friend)

    @Query("UPDATE friends SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    // --- Update Proximity Timestamps ---

    @Query(
        "UPDATE friends SET lastSeenTimestamp = :timestamp, " +
            "lastSeenDistanceMeters = :distanceMeters, lastSeenRssi = :rssi " +
            "WHERE id = :id"
    )
    suspend fun updateProximityTimestamp(
        id: Long,
        timestamp: Long,
        distanceMeters: Float,
        rssi: Int
    )

    @Query(
        "UPDATE friends SET lastSeenTimestamp = :timestamp " +
            "WHERE UPPER(deviceIdentifier) = UPPER(:identifier) " +
            "OR UPPER(beaconId) = UPPER(:identifier) " +
            "OR UPPER(macAddress) = UPPER(:identifier)"
    )
    suspend fun updateProximityTimestampByIdentifier(identifier: String, timestamp: Long)

    // --- Delete ---

    @Query("DELETE FROM friends WHERE id = :id")
    suspend fun deleteFriendById(id: Long)

    @Query("DELETE FROM friends")
    suspend fun deleteAllFriends()
}
