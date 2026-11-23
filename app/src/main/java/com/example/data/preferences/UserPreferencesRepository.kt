package com.example.data.preferences

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.SecureRandom
import java.util.Locale

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "whoisnearme_prefs")

data class UserPreferences(
    val displayName: String,
    val myBeaconId: String,
    val autoScanOnLaunch: Boolean,
    val broadcastEnabled: Boolean,
    val alertRangeMeters: Float,
    val showUnknownBleDevices: Boolean,
    val backgroundServiceEnabled: Boolean = false,
    val proximityNotificationsEnabled: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val MY_BEACON_ID = stringPreferencesKey("my_beacon_id")
        val AUTO_SCAN = booleanPreferencesKey("auto_scan")
        val BROADCAST_ENABLED = booleanPreferencesKey("broadcast_enabled")
        val ALERT_RANGE_METERS = floatPreferencesKey("alert_range_meters")
        val SHOW_UNKNOWN_BLE = booleanPreferencesKey("show_unknown_ble")
        val BACKGROUND_SERVICE_ENABLED = booleanPreferencesKey("background_service_enabled")
        val PROXIMITY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("proximity_notifications_enabled")
        val HAPTIC_FEEDBACK_ENABLED = booleanPreferencesKey("haptic_feedback_enabled")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            displayName = prefs[Keys.DISPLAY_NAME] ?: defaultDeviceName(),
            myBeaconId = prefs[Keys.MY_BEACON_ID] ?: "",
            autoScanOnLaunch = prefs[Keys.AUTO_SCAN] ?: true,
            broadcastEnabled = prefs[Keys.BROADCAST_ENABLED] ?: true,
            alertRangeMeters = prefs[Keys.ALERT_RANGE_METERS] ?: 10f,
            showUnknownBleDevices = prefs[Keys.SHOW_UNKNOWN_BLE] ?: true,
            backgroundServiceEnabled = prefs[Keys.BACKGROUND_SERVICE_ENABLED] ?: false,
            proximityNotificationsEnabled = prefs[Keys.PROXIMITY_NOTIFICATIONS_ENABLED] ?: true,
            hapticFeedbackEnabled = prefs[Keys.HAPTIC_FEEDBACK_ENABLED] ?: true
        )
    }

    suspend fun ensureBeaconIdInitialized(): String {
        val current = context.dataStore.data.first()[Keys.MY_BEACON_ID]
        if (!current.isNullOrBlank()) return current
        val generated = generateBeaconId()
        context.dataStore.edit { prefs ->
            if (prefs[Keys.MY_BEACON_ID].isNullOrBlank()) {
                prefs[Keys.MY_BEACON_ID] = generated
            }
            if (prefs[Keys.DISPLAY_NAME].isNullOrBlank()) {
                prefs[Keys.DISPLAY_NAME] = defaultDeviceName()
            }
        }
        return context.dataStore.data.first()[Keys.MY_BEACON_ID] ?: generated
    }

    suspend fun regenerateBeaconId(): String {
        val generated = generateBeaconId()
        context.dataStore.edit { prefs ->
            prefs[Keys.MY_BEACON_ID] = generated
        }
        return generated
    }

    suspend fun updateDisplayName(name: String) {
        val clean = name.trim().take(24).ifEmpty { defaultDeviceName() }
        context.dataStore.edit { prefs ->
            prefs[Keys.DISPLAY_NAME] = clean
        }
    }

    suspend fun setAutoScanOnLaunch(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTO_SCAN] = enabled
        }
    }

    suspend fun setBroadcastEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BROADCAST_ENABLED] = enabled
        }
    }

    suspend fun setAlertRangeMeters(meters: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ALERT_RANGE_METERS] = meters.coerceIn(2f, 25f)
        }
    }

    suspend fun setShowUnknownBleDevices(show: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SHOW_UNKNOWN_BLE] = show
        }
    }

    suspend fun setBackgroundServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BACKGROUND_SERVICE_ENABLED] = enabled
        }
    }

    suspend fun setProximityNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PROXIMITY_NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun setHapticFeedbackEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HAPTIC_FEEDBACK_ENABLED] = enabled
        }
    }

    private fun defaultDeviceName(): String {
        val model = Build.MODEL?.trim().orEmpty()
        return if (model.isNotEmpty()) model.take(20) else "My Phone"
    }

    companion object {
        fun generateBeaconId(): String {
            val random = SecureRandom()
            val bytes = ByteArray(4)
            random.nextBytes(bytes)
            val hex = bytes.joinToString("") { String.format(Locale.US, "%02X", it) }
            return "${hex.substring(0, 4)}-${hex.substring(4, 8)}"
        }
    }
}
