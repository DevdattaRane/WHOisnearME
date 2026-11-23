package com.example

import android.app.Application
import com.example.data.ble.BleProximityManager
import com.example.data.local.AppDatabase
import com.example.data.notifications.ProximityNotificationService
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.FriendRepository

class WhoIsNearMeApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val preferencesRepository: UserPreferencesRepository by lazy { UserPreferencesRepository(this) }
    val bleProximityManager: BleProximityManager by lazy { BleProximityManager(this) }
    val proximityNotificationService: ProximityNotificationService by lazy {
        ProximityNotificationService(this)
    }
    val friendRepository: FriendRepository by lazy {
        FriendRepository(
            friendDao = database.friendDao(),
            sightingDao = database.sightingDao(),
            blockedDeviceDao = database.blockedDeviceDao(),
            preferencesRepository = preferencesRepository,
            bleManager = bleProximityManager
        )
    }
}
