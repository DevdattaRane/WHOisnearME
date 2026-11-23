package com.example.data.model

import com.example.data.local.entity.Friend
import java.util.Locale
import kotlin.math.pow

enum class ProximityZone(val label: String, val maxMeters: Float) {
    IMMEDIATE("Immediate", 2.0f),
    NEAR("Near", 8.0f),
    FAR("Far", 25.0f)
}

enum class BleRadioState {
    READY,
    PERMISSION_REQUIRED,
    BLUETOOTH_OFF,
    BLE_UNAVAILABLE
}

data class DetectedPeer(
    val key: String,
    val beaconId: String,
    val macAddress: String,
    val advertisedName: String,
    val rssi: Int,
    val estimatedMeters: Float,
    val isWhoIsNearMeApp: Boolean,
    val lastUpdatedMs: Long
) {
    val zone: ProximityZone
        get() = when {
            estimatedMeters <= ProximityZone.IMMEDIATE.maxMeters -> ProximityZone.IMMEDIATE
            estimatedMeters <= ProximityZone.NEAR.maxMeters -> ProximityZone.NEAR
            else -> ProximityZone.FAR
        }
}

data class FriendWithProximity(
    val friend: Friend,
    val activeDetection: DetectedPeer?,
    val matchedBy: String?,
    val isBlocked: Boolean = false
) {
    val isCurrentlyNearby: Boolean
        get() = activeDetection != null && !isBlocked

    val currentZone: ProximityZone?
        get() = activeDetection?.zone
}

data class FriendFormValidation(
    val isValid: Boolean,
    val nameError: String? = null,
    val beaconIdError: String? = null,
    val macError: String? = null,
    val generalError: String? = null,
    val normalizedName: String = "",
    val normalizedNickname: String = "",
    val normalizedBeaconId: String = "",
    val normalizedMac: String = "",
    val primaryDeviceIdentifier: String = ""
)

object ProximityMath {
    const val DEFAULT_TX_POWER_AT_1M = -59.0
    const val DEFAULT_PATH_LOSS_EXPONENT = 2.2

    /**
     * Translates a Bluetooth LE RSSI reading (in dBm) into an approximate physical distance in meters
     * using the log-distance path loss model: d = 10 ^ ((TxPower_1m - RSSI) / (10 * n)).
     */
    fun rssiToDistanceMeters(
        rssi: Int,
        measuredPowerAt1mDbm: Double = DEFAULT_TX_POWER_AT_1M,
        pathLossExponent: Double = DEFAULT_PATH_LOSS_EXPONENT
    ): Float {
        if (rssi >= 0) return 0.3f
        val safeExponent = pathLossExponent.coerceIn(1.5, 4.0)
        val exponent = (measuredPowerAt1mDbm - rssi) / (10.0 * safeExponent)
        val raw = 10.0.pow(exponent).toFloat()
        return raw.coerceIn(0.3f, 35.0f)
    }

    fun estimateDistanceMeters(
        rssi: Int,
        measuredPowerAt1mDbm: Double = DEFAULT_TX_POWER_AT_1M
    ): Float = rssiToDistanceMeters(
        rssi = rssi,
        measuredPowerAt1mDbm = measuredPowerAt1mDbm
    )

    /**
     * Converts an advertised BLE ScanRecord txPowerLevel (at 0m, if present) to an approximate
     * 1-meter reference RSSI, or returns [DEFAULT_TX_POWER_AT_1M] if unavailable.
     */
    fun calibrateMeasuredPowerAt1m(advertisedTxPowerAt0m: Int?): Double {
        if (advertisedTxPowerAt0m == null || advertisedTxPowerAt0m !in -40..20) {
            return DEFAULT_TX_POWER_AT_1M
        }
        return (advertisedTxPowerAt0m - 41.0).coerceIn(-75.0, -45.0)
    }

    fun formatDistance(meters: Float): String {
        return if (meters < 1.0f) {
            String.format(Locale.US, "%.1f m", meters)
        } else if (meters < 10.0f) {
            String.format(Locale.US, "~%.1f m", meters)
        } else {
            String.format(Locale.US, "~%.0f m", meters)
        }
    }

    fun formatRadarRingMeters(meters: Float): String {
        return if (meters < 10f && (meters % 1f) >= 0.15f && (meters % 1f) <= 0.85f) {
            String.format(Locale.US, "%.1fm", meters)
        } else {
            "${kotlin.math.round(meters).toInt()}m"
        }
    }
}

object IdentifierValidator {
    private val MAC_REGEX = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")
    private val BEACON_HEX_REGEX = Regex("^[0-9A-Fa-f]{8}$")
    private const val ANDROID_MASKED_MAC = "02:00:00:00:00:00"

    fun normalizeBeaconId(input: String): String {
        val stripped = input.trim().uppercase(Locale.US).replace("-", "").replace(" ", "")
        return if (stripped.length == 8 && BEACON_HEX_REGEX.matches(stripped)) {
            "${stripped.substring(0, 4)}-${stripped.substring(4, 8)}"
        } else {
            input.trim().uppercase(Locale.US)
        }
    }

    fun normalizeMacAddress(input: String): String {
        return input.trim().uppercase(Locale.US).replace("-", ":")
    }

    fun isValidBeaconId(input: String): Boolean {
        val stripped = input.trim().uppercase(Locale.US).replace("-", "").replace(" ", "")
        return BEACON_HEX_REGEX.matches(stripped)
    }

    fun isValidMacAddress(input: String): Boolean {
        val normalized = normalizeMacAddress(input)
        return MAC_REGEX.matches(normalized) && normalized != ANDROID_MASKED_MAC
    }

    fun validateFriendForm(
        name: String,
        nickname: String = "",
        beaconId: String,
        macAddress: String,
        existingFriends: List<Friend>,
        editingFriendId: Long?
    ): FriendFormValidation {
        val trimmedName = name.trim()
        val trimmedNickname = nickname.trim().take(30)
        val normBeacon = if (beaconId.isBlank()) "" else normalizeBeaconId(beaconId)
        val normMac = if (macAddress.isBlank()) "" else normalizeMacAddress(macAddress)

        var nameError: String? = null
        var beaconError: String? = null
        var macError: String? = null
        var generalError: String? = null

        if (trimmedName.isEmpty()) {
            nameError = "Enter a friend's name."
        } else if (trimmedName.length > 40) {
            nameError = "Name must be 40 characters or fewer."
        }

        if (normBeacon.isEmpty() && normMac.isEmpty()) {
            generalError = "Provide at least a WHOisnearME Beacon ID or a Bluetooth MAC address."
        }

        if (normBeacon.isNotEmpty() && !isValidBeaconId(normBeacon)) {
            beaconError = "Use 8 hex characters (e.g. A7F2-9C14)."
        }

        if (normMac.isNotEmpty()) {
            if (normMac == ANDROID_MASKED_MAC) {
                macError = "02:00:00:00:00:00 is Android's masked placeholder MAC. Use their Beacon ID instead."
            } else if (!MAC_REGEX.matches(normMac)) {
                macError = "Use format AA:BB:CC:DD:EE:FF."
            }
        }

        val others = existingFriends.filter { it.id != editingFriendId }
        if (beaconError == null && normBeacon.isNotEmpty() &&
            others.any { it.beaconId.equals(normBeacon, ignoreCase = true) }
        ) {
            beaconError = "This Beacon ID is already saved for another friend."
        }
        if (macError == null && normMac.isNotEmpty() &&
            others.any { it.macAddress.equals(normMac, ignoreCase = true) }
        ) {
            macError = "This MAC address is already saved for another friend."
        }

        val valid = nameError == null && beaconError == null && macError == null && generalError == null
        return FriendFormValidation(
            isValid = valid,
            nameError = nameError,
            beaconIdError = beaconError,
            macError = macError,
            generalError = generalError,
            normalizedName = trimmedName,
            normalizedNickname = trimmedNickname,
            normalizedBeaconId = normBeacon,
            normalizedMac = normMac,
            primaryDeviceIdentifier = normBeacon.ifBlank { normMac }
        )
    }
}
