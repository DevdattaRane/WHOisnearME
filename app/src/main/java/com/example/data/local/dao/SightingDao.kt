package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.SightingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SightingDao {
    @Query("SELECT * FROM sightings ORDER BY timestamp DESC LIMIT 100")
    fun getRecentSightings(): Flow<List<SightingEntity>>

    @Query("SELECT * FROM sightings WHERE friendId = :friendId ORDER BY timestamp DESC LIMIT 50")
    fun getSightingsForFriend(friendId: Long): Flow<List<SightingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSighting(sighting: SightingEntity)

    @Query("DELETE FROM sightings")
    suspend fun clearAllSightings()
}
