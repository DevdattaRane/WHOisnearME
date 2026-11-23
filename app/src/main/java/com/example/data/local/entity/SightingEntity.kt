package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sightings",
    foreignKeys = [
        ForeignKey(
            entity = Friend::class,
            parentColumns = ["id"],
            childColumns = ["friendId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["friendId"]), Index(value = ["timestamp"])]
)
data class SightingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val friendId: Long,
    val friendName: String,
    val matchedBy: String,
    val rssi: Int,
    val estimatedMeters: Float,
    val timestamp: Long = System.currentTimeMillis()
)
