package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "blocked_devices",
    indices = [Index(value = ["identifier"], unique = true)]
)
data class BlockedDevice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val identifier: String,
    val label: String = "",
    val blockedAt: Long = System.currentTimeMillis()
)
