package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.BlockedDevice
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedDeviceDao {

    @Query("SELECT * FROM blocked_devices ORDER BY blockedAt DESC")
    fun getAllBlockedDevices(): Flow<List<BlockedDevice>>

    @Query("SELECT identifier FROM blocked_devices")
    suspend fun getAllBlockedIdentifiersSnapshot(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_devices WHERE UPPER(identifier) = UPPER(:identifier))")
    suspend fun isIdentifierBlocked(identifier: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun blockDevice(device: BlockedDevice): Long

    @Query("DELETE FROM blocked_devices WHERE id = :id")
    suspend fun unblockById(id: Long)

    @Query("DELETE FROM blocked_devices WHERE UPPER(identifier) = UPPER(:identifier)")
    suspend fun unblockByIdentifier(identifier: String)

    @Query("DELETE FROM blocked_devices")
    suspend fun clearAllBlockedDevices()
}
