package com.example.data.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.example.data.model.BleRadioState
import com.example.data.model.DetectedPeer
import com.example.data.model.IdentifierValidator
import com.example.data.model.ProximityMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

class BleProximityManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _radioState = MutableStateFlow(computeRadioState())
    val radioState: StateFlow<BleRadioState> = _radioState.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _statusError = MutableStateFlow<String?>(null)
    val statusError: StateFlow<String?> = _statusError.asStateFlow()

    private val _detectedPeers = MutableStateFlow<List<DetectedPeer>>(emptyList())
    val detectedPeers: StateFlow<List<DetectedPeer>> = _detectedPeers.asStateFlow()

    private val peerCache = ConcurrentHashMap<String, DetectedPeer>()
    private var pruneJob: Job? = null
    private var receiverRegistered = false

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                refreshRadioState()
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                    stopScanning()
                    stopAdvertising()
                }
            }
        }
    }

    init {
        registerReceiverIfNeeded()
        startPruneLoop()
    }

    private fun registerReceiverIfNeeded() {
        if (receiverRegistered) return
        try {
            val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            ContextCompat.registerReceiver(
                context,
                bluetoothStateReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        } catch (_: Exception) {
        }
    }

    fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun hasAllPermissions(): Boolean {
        return requiredPermissions().all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun isBleSupported(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) &&
            bluetoothAdapter != null
    }

    fun refreshRadioState(): BleRadioState {
        val state = computeRadioState()
        _radioState.value = state
        return state
    }

    private fun computeRadioState(): BleRadioState {
        if (!isBleSupported()) return BleRadioState.BLE_UNAVAILABLE
        if (!hasAllPermissions()) return BleRadioState.PERMISSION_REQUIRED
        return if (bluetoothAdapter?.isEnabled == true) {
            BleRadioState.READY
        } else {
            BleRadioState.BLUETOOTH_OFF
        }
    }

    @SuppressLint("MissingPermission")
    fun startScanning() {
        _statusError.value = null
        val state = refreshRadioState()
        if (state != BleRadioState.READY) {
            _isScanning.value = false
            return
        }

        val scanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            _statusError.value = "Bluetooth LE scanner is unavailable on this device."
            _isScanning.value = false
            return
        }

        if (_isScanning.value) return

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            // Unfiltered scan allows matching both WHOisnearME service beacons and saved MAC addresses
            scanner.startScan(emptyList<ScanFilter>(), settings, scanCallback)
            _isScanning.value = true
        } catch (e: SecurityException) {
            _statusError.value = "Nearby device permission is required to scan."
            _isScanning.value = false
            refreshRadioState()
        } catch (e: Exception) {
            _statusError.value = "Could not start radar scan: ${e.message ?: "Bluetooth error"}"
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (!_isScanning.value) return
        try {
            if (hasAllPermissions()) {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            }
        } catch (_: Exception) {
        } finally {
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun startAdvertising(beaconId: String, displayName: String) {
        val state = refreshRadioState()
        if (state != BleRadioState.READY || beaconId.isBlank()) {
            _isAdvertising.value = false
            return
        }

        val advertiser: BluetoothLeAdvertiser? = bluetoothAdapter?.bluetoothLeAdvertiser
        if (advertiser == null) {
            _statusError.value = "This device does not support BLE beacon broadcasting."
            _isAdvertising.value = false
            return
        }

        stopAdvertising()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false)
            .build()

        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(SERVICE_PARCEL_UUID)
            .build()

        val payload = encodeBeaconPayload(beaconId, displayName)
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceData(SERVICE_PARCEL_UUID, payload)
            .build()

        try {
            advertiser.startAdvertising(settings, advertiseData, scanResponse, advertiseCallback)
        } catch (e: SecurityException) {
            _statusError.value = "Bluetooth advertise permission is required to broadcast presence."
            _isAdvertising.value = false
            refreshRadioState()
        } catch (e: Exception) {
            _statusError.value = "Could not broadcast beacon: ${e.message ?: "Bluetooth error"}"
            _isAdvertising.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        try {
            if (hasAllPermissions()) {
                bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            }
        } catch (_: Exception) {
        } finally {
            _isAdvertising.value = false
        }
    }

    fun clearError() {
        _statusError.value = null
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
            _statusError.value = "BLE scan failed (code $errorCode). Try toggling Bluetooth."
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            _isAdvertising.value = true
        }

        override fun onStartFailure(errorCode: Int) {
            _isAdvertising.value = false
            _statusError.value = when (errorCode) {
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "BLE advertising is unsupported on this hardware."
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "Beacon packet exceeded BLE size limit."
                else -> "Could not start presence broadcast (code $errorCode)."
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun ingestScanResult(result: ScanResult): DetectedPeer? {
        val parsed = parseScanResult(result) ?: return null
        val prev = peerCache[parsed.key]
        val smoothedRssi = if (prev != null) {
            ((prev.rssi * 0.6f) + (parsed.rssi * 0.4f)).roundToInt()
        } else {
            parsed.rssi
        }
        val measuredPowerAt1m = extractMeasuredPowerAt1m(result)

        val peer = parsed.copy(
            rssi = smoothedRssi,
            estimatedMeters = ProximityMath.rssiToDistanceMeters(
                rssi = smoothedRssi,
                measuredPowerAt1mDbm = measuredPowerAt1m
            )
        )
        peerCache[parsed.key] = peer
        publishPeers()
        return peer
    }

    private fun extractMeasuredPowerAt1m(result: ScanResult): Double {
        val recordTx = result.scanRecord?.txPowerLevel?.takeIf { it != Int.MIN_VALUE }
        val apiTx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            result.txPower.takeIf { it != ScanResult.TX_POWER_NOT_PRESENT }
        } else {
            null
        }
        return ProximityMath.calibrateMeasuredPowerAt1m(recordTx ?: apiTx)
    }

    @SuppressLint("MissingPermission")
    private fun parseScanResult(result: ScanResult): DetectedPeer? {
        val mac = result.device?.address?.uppercase(Locale.US).orEmpty()
        if (mac.isBlank()) return null

        val record = result.scanRecord
        val serviceData = record?.getServiceData(SERVICE_PARCEL_UUID)
        val advertisedDeviceName = try {
            record?.deviceName ?: if (hasAllPermissions()) result.device?.name else null
        } catch (_: SecurityException) {
            null
        }.orEmpty()

        val hasWhoIsNearMeUuid = record?.serviceUuids?.contains(SERVICE_PARCEL_UUID) == true ||
            serviceData != null ||
            advertisedDeviceName.contains("WHOisnearME", ignoreCase = true)

        var decodedBeaconId = ""
        var decodedName = ""
        if (serviceData != null && serviceData.size >= 4) {
            decodedBeaconId = decodeBeaconId(serviceData.copyOfRange(0, 4))
            if (serviceData.size > 4) {
                decodedName = String(
                    serviceData.copyOfRange(4, serviceData.size),
                    StandardCharsets.UTF_8
                ).trim()
            }
        }

        val key = if (decodedBeaconId.isNotEmpty()) "BEACON:$decodedBeaconId" else "MAC:$mac"
        val measuredPowerAt1m = extractMeasuredPowerAt1m(result)
        return DetectedPeer(
            key = key,
            beaconId = decodedBeaconId,
            macAddress = mac,
            advertisedName = decodedName.ifBlank { advertisedDeviceName },
            rssi = result.rssi,
            estimatedMeters = ProximityMath.rssiToDistanceMeters(
                rssi = result.rssi,
                measuredPowerAt1mDbm = measuredPowerAt1m
            ),
            isWhoIsNearMeApp = hasWhoIsNearMeUuid,
            lastUpdatedMs = System.currentTimeMillis()
        )
    }

    private fun handleScanResult(result: ScanResult) {
        ingestScanResult(result)
    }

    private fun startPruneLoop() {
        pruneJob?.cancel()
        pruneJob = scope.launch {
            while (isActive) {
                delay(3000L)
                val cutoff = System.currentTimeMillis() - PEER_STALE_MS
                var removed = false
                val iterator = peerCache.entries.iterator()
                while (iterator.hasNext()) {
                    if (iterator.next().value.lastUpdatedMs < cutoff) {
                        iterator.remove()
                        removed = true
                    }
                }
                if (removed) publishPeers()
            }
        }
    }

    private fun publishPeers() {
        _detectedPeers.value = peerCache.values
            .sortedWith(
                compareByDescending<DetectedPeer> { it.isWhoIsNearMeApp }
                    .thenBy { it.estimatedMeters }
            )
    }

    fun release() {
        stopScanning()
        stopAdvertising()
        pruneJob?.cancel()
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(bluetoothStateReceiver)
            } catch (_: Exception) {
            }
            receiverRegistered = false
        }
    }

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("7A3F1000-8C4B-4E2A-9D11-57484F4E4541")
        val SERVICE_PARCEL_UUID = ParcelUuid(SERVICE_UUID)
        private const val PEER_STALE_MS = 15_000L

        fun encodeBeaconPayload(beaconId: String, displayName: String): ByteArray {
            val cleanHex = IdentifierValidator.normalizeBeaconId(beaconId)
                .replace("-", "")
                .padEnd(8, '0')
                .take(8)
            val idBytes = ByteArray(4) { i ->
                cleanHex.substring(i * 2, i * 2 + 2).toIntOrNull(16)?.toByte() ?: 0
            }
            val nameBytes = displayName.trim().toByteArray(StandardCharsets.UTF_8).take(8).toByteArray()
            return idBytes + nameBytes
        }

        fun decodeBeaconId(bytes: ByteArray): String {
            if (bytes.size < 4) return ""
            val hex = bytes.take(4).joinToString("") { String.format(Locale.US, "%02X", it) }
            return "${hex.substring(0, 4)}-${hex.substring(4, 8)}"
        }
    }
}
