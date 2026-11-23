package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.BlockedDeviceDao
import com.example.data.local.dao.FriendDao
import com.example.data.local.dao.SightingDao
import com.example.data.local.entity.BlockedDevice
import com.example.data.local.entity.Friend
import com.example.data.local.entity.SightingEntity

@Database(
    entities = [Friend::class, SightingEntity::class, BlockedDevice::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun friendDao(): FriendDao
    abstract fun sightingDao(): SightingDao
    abstract fun blockedDeviceDao(): BlockedDeviceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "whoisnearme_db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
