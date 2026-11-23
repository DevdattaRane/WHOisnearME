package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "friends",
    indices = [
        Index(value = ["deviceIdentifier"]),
        Index(value = ["beaconId"]),
        Index(value = ["macAddress"])
    ]
)
data class Friend(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val nickname: String = "",
    val deviceIdentifier: String = "",
    val beaconId: String = "",
    val macAddress: String = "",
    val note: String = "",
    val avatarColorHex: Long = 0xFF00A896,
    val isFavorite: Boolean = false,
    val lastSeenTimestamp: Long? = null,
    val lastSeenDistanceMeters: Float? = null,
    val lastSeenRssi: Int? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val displayLabel: String
        get() = if (nickname.isNotBlank()) nickname else name
}
