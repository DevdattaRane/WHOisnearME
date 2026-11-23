package com.example.data.ble

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.WhoIsNearMeApp
import com.example.data.local.entity.Friend
import com.example.data.local.entity.SightingEntity
import com.example.data.model.DetectedPeer
import com.example.ui.theme.FriendAvatarColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Foreground-capable background Service that uses [BluetoothAdapter] and [ScanCallback]
 * to continuously discover nearby devices advertising as WHOisnearME and persist newly
 * discovered friends and proximity sightings into the local Room database.
 */
class WhoIsNearMeScannerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dbMutex = Mutex()
    private val lastSightingTime = ConcurrentHashMap<Long, Long>()

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var isScanning = false
    private var discoveredSessionCount = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = manager?.adapter
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopScanningAndSelf()
            return START_NOT_STICKY
        }

        if (!hasRequiredScanPermission()) {
            _lastServiceMessage.value = "Nearby scan permission is missing for background service."
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification("Listening for WHOisnearME devices nearby...")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: SecurityException) {
            _lastServiceMessage.value = "Cannot start connectedDevice service without Bluetooth permissions."
            stopSelf()
            return START_NOT_STICKY
        }

        _isRunning.value = true
        startBleDiscovery()
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startBleDiscovery() {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _lastServiceMessage.value = "Bluetooth is turned off. Background scan paused."
            updateNotification("Bluetooth is off — turn on Bluetooth to resume scanning")
            return
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            _lastServiceMessage.value = "Bluetooth LE scanner unavailable on this device."
            stopScanningAndSelf()
            return
        }

        if (isScanning) return
        bleScanner = scanner

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()

        // Scan unfiltered and evaluate WHOisnearME service UUID / name / saved MACs in ScanCallback
        try {
            scanner.startScan(emptyList<ScanFilter>(), settings, scanCallback)
            isScanning = true
            _lastServiceMessage.value = null
        } catch (e: SecurityException) {
            _lastServiceMessage.value = "Bluetooth scan permission denied."
            stopScanningAndSelf()
        } catch (e: Exception) {
            _lastServiceMessage.value = "Background scan error: ${e.message ?: "Unknown"}"
            stopScanningAndSelf()
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            processScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { processScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            _lastServiceMessage.value = "Background BLE scan failed (code $errorCode)."
            updateNotification("Background scan paused (error $errorCode)")
        }
    }

    private fun processScanResult(result: ScanResult) {
        val app = application as WhoIsNearMeApp
        val peer = app.bleProximityManager.ingestScanResult(result) ?: return

        serviceScope.launch {
            handleDiscoveredPeerInDatabase(app, peer)
        }
    }

    private suspend fun handleDiscoveredPeerInDatabase(
        app: WhoIsNearMeApp,
        peer: DetectedPeer
    ) {
        val friendDao = app.database.friendDao()
        val sightingDao = app.database.sightingDao()
        val blockedDeviceDao = app.database.blockedDeviceDao()
        val now = System.currentTimeMillis()

        dbMutex.withLock {
            val isBeaconBlocked = peer.beaconId.isNotBlank() &&
                blockedDeviceDao.isIdentifierBlocked(peer.beaconId)
            val isMacBlocked = peer.macAddress.isNotBlank() &&
                blockedDeviceDao.isIdentifierBlocked(peer.macAddress)
            if (isBeaconBlocked || isMacBlocked) {
                return
            }

            val existingByBeacon = if (peer.beaconId.isNotBlank()) {
                friendDao.findFriendByDeviceIdentifier(peer.beaconId)
            } else null

            val existing = existingByBeacon
                ?: friendDao.findFriendByDeviceIdentifier(peer.macAddress)

            if (existing != null) {
                val matchedBy = if (existingByBeacon != null) "BEACON_ID" else "MAC_ADDRESS"
                val canonicalId = existing.beaconId.ifBlank { existing.macAddress }
                val prefs = app.preferencesRepository.preferencesFlow.first()
                app.proximityNotificationService.notifyFriendDetectedInBackground(
                    canonicalIdentifier = canonicalId,
                    displayName = existing.displayLabel,
                    distanceMeters = peer.estimatedMeters,
                    isNewlyDiscovered = existing.lastSeenTimestamp == null,
                    prefs = prefs,
                    nowMs = now
                )

                val lastLogged = lastSightingTime[existing.id] ?: 0L
                if (now - lastLogged >= SIGHTING_DEBOUNCE_MS) {
                    lastSightingTime[existing.id] = now
                    friendDao.updateProximityTimestamp(
                        id = existing.id,
                        timestamp = now,
                        distanceMeters = peer.estimatedMeters,
                        rssi = peer.rssi
                    )
                    sightingDao.insertSighting(
                        SightingEntity(
                            friendId = existing.id,
                            friendName = existing.displayLabel,
                            matchedBy = matchedBy,
                            rssi = peer.rssi,
                            estimatedMeters = peer.estimatedMeters,
                            timestamp = now
                        )
                    )
                    updateNotification("${existing.displayLabel} is nearby (~${"%.1f".format(peer.estimatedMeters)} m)")
                }
            } else if (peer.isWhoIsNearMeApp) {
                // New peer advertising as WHOisnearME discovered: add persistently to Room!
                val primaryIdentifier = peer.beaconId.ifBlank { peer.macAddress }
                val discoveredName = peer.advertisedName
                    .takeIf { it.isNotBlank() && !it.equals("WHOisnearME", ignoreCase = true) }
                    ?: "WHOisnearME Friend"
                val colorIndex = abs(primaryIdentifier.hashCode()) % FriendAvatarColors.size

                val newFriend = Friend(
                    name = discoveredName,
                    nickname = "",
                    deviceIdentifier = primaryIdentifier,
                    beaconId = peer.beaconId,
                    macAddress = peer.macAddress,
                    note = "Auto-discovered nearby via WHOisnearME beacon",
                    avatarColorHex = FriendAvatarColors[colorIndex],
                    isFavorite = false,
                    lastSeenTimestamp = now,
                    lastSeenDistanceMeters = peer.estimatedMeters,
                    lastSeenRssi = peer.rssi,
                    createdAt = now
                )
                val insertedId = friendDao.insertFriend(newFriend)
                lastSightingTime[insertedId] = now

                sightingDao.insertSighting(
                    SightingEntity(
                        friendId = insertedId,
                        friendName = discoveredName,
                        matchedBy = if (peer.beaconId.isNotBlank()) "BEACON_ID" else "MAC_ADDRESS",
                        rssi = peer.rssi,
                        estimatedMeters = peer.estimatedMeters,
                        timestamp = now
                    )
                )

                val prefs = app.preferencesRepository.preferencesFlow.first()
                app.proximityNotificationService.notifyFriendDetectedInBackground(
                    canonicalIdentifier = primaryIdentifier,
                    displayName = discoveredName,
                    distanceMeters = peer.estimatedMeters,
                    isNewlyDiscovered = true,
                    prefs = prefs,
                    nowMs = now
                )

                discoveredSessionCount += 1
                updateNotification(
                    "Found new friend: $discoveredName ($discoveredSessionCount discovered)"
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScanningAndSelf() {
        if (isScanning && hasRequiredScanPermission()) {
            try {
                bleScanner?.stopScan(scanCallback)
            } catch (_: Exception) {
            }
        }
        isScanning = false
        _isRunning.value = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun hasRequiredScanPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WHOisnearME Background Radar",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Discovers nearby friends advertising as WHOisnearME"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, WhoIsNearMeScannerService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("WHOisnearME Radar Active")
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        if (isScanning && hasRequiredScanPermission()) {
            try {
                bleScanner?.stopScan(scanCallback)
            } catch (_: Exception) {
            }
        }
        isScanning = false
        _isRunning.value = false
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "whoisnearme_bg_radar"
        private const val NOTIFICATION_ID = 4102
        private const val ACTION_STOP = "com.example.action.STOP_SCAN_SERVICE"
        private const val SIGHTING_DEBOUNCE_MS = 30_000L

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _lastServiceMessage = MutableStateFlow<String?>(null)
        val lastServiceMessage: StateFlow<String?> = _lastServiceMessage.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, WhoIsNearMeScannerService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, WhoIsNearMeScannerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
