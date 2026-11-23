package com.example.data.repository

import com.example.data.ble.BleProximityManager
import com.example.data.local.dao.BlockedDeviceDao
import com.example.data.local.dao.FriendDao
import com.example.data.local.dao.SightingDao
import com.example.data.local.entity.BlockedDevice
import com.example.data.local.entity.Friend
import com.example.data.local.entity.SightingEntity
import com.example.data.model.DetectedPeer
import com.example.data.model.FriendFormValidation
import com.example.data.model.FriendWithProximity
import com.example.data.model.IdentifierValidator
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class FriendRepository(
    private val friendDao: FriendDao,
    private val sightingDao: SightingDao,
    private val blockedDeviceDao: BlockedDeviceDao,
    private val preferencesRepository: UserPreferencesRepository,
    val bleManager: BleProximityManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lastSightingLoggedAt = ConcurrentHashMap<Long, Long>()

    val friendsFlow: Flow<List<Friend>> = friendDao.getAllFriends()
    val sightingsFlow: Flow<List<SightingEntity>> = sightingDao.getRecentSightings()
    val blockedDevicesFlow: Flow<List<BlockedDevice>> = blockedDeviceDao.getAllBlockedDevices()
    val userPreferencesFlow: Flow<UserPreferences> = preferencesRepository.preferencesFlow

    val friendsWithProximityFlow: Flow<List<FriendWithProximity>> = combine(
        friendsFlow,
        bleManager.detectedPeers,
        blockedDevicesFlow
    ) { friends, peers, blockedDevices ->
        val blockedSet = blockedDevices.map { it.identifier.uppercase(Locale.US) }.toSet()
        friends.map { friend ->
            val friendBlocked = isFriendBlocked(friend, blockedSet)
            if (friendBlocked) {
                FriendWithProximity(
                    friend = friend,
                    activeDetection = null,
                    matchedBy = null,
                    isBlocked = true
                )
            } else {
                val visiblePeers = peers.filterNot { isPeerBlocked(it, blockedSet) }
                val beaconMatch = if (friend.beaconId.isNotBlank()) {
                    visiblePeers.firstOrNull { it.beaconId.equals(friend.beaconId, ignoreCase = true) }
                } else null

                val macMatch = if (beaconMatch == null && friend.macAddress.isNotBlank()) {
                    visiblePeers.firstOrNull { it.macAddress.equals(friend.macAddress, ignoreCase = true) }
                } else null

                val identifierMatch = if (beaconMatch == null && macMatch == null && friend.deviceIdentifier.isNotBlank()) {
                    visiblePeers.firstOrNull {
                        it.beaconId.equals(friend.deviceIdentifier, ignoreCase = true) ||
                            it.macAddress.equals(friend.deviceIdentifier, ignoreCase = true)
                    }
                } else null

                val match = beaconMatch ?: macMatch ?: identifierMatch
                val matchedBy = when {
                    beaconMatch != null -> "BEACON_ID"
                    macMatch != null -> "MAC_ADDRESS"
                    identifierMatch != null -> "DEVICE_IDENTIFIER"
                    else -> null
                }

                if (match != null && matchedBy != null) {
                    recordSightingIfNeeded(friend, match, matchedBy)
                }

                FriendWithProximity(
                    friend = friend,
                    activeDetection = match,
                    matchedBy = matchedBy,
                    isBlocked = false
                )
            }
        }.sortedWith(
            compareByDescending<FriendWithProximity> { it.isCurrentlyNearby }
                .thenBy { it.activeDetection?.estimatedMeters ?: Float.MAX_VALUE }
                .thenByDescending { it.friend.isFavorite }
                .thenBy { it.friend.name.lowercase() }
        )
    }

    fun observeFriendDetail(friendId: Long): Flow<FriendWithProximity?> {
        return combine(
            friendDao.observeFriendById(friendId),
            bleManager.detectedPeers,
            blockedDevicesFlow
        ) { friend, peers, blockedDevices ->
            friend?.let {
                val blockedSet = blockedDevices.map { b -> b.identifier.uppercase(Locale.US) }.toSet()
                val friendBlocked = isFriendBlocked(it, blockedSet)
                if (friendBlocked) {
                    FriendWithProximity(
                        friend = it,
                        activeDetection = null,
                        matchedBy = null,
                        isBlocked = true
                    )
                } else {
                    val visiblePeers = peers.filterNot { p -> isPeerBlocked(p, blockedSet) }
                    val beaconMatch = if (it.beaconId.isNotBlank()) {
                        visiblePeers.firstOrNull { p -> p.beaconId.equals(it.beaconId, ignoreCase = true) }
                    } else null
                    val macMatch = if (beaconMatch == null && it.macAddress.isNotBlank()) {
                        visiblePeers.firstOrNull { p -> p.macAddress.equals(it.macAddress, ignoreCase = true) }
                    } else null
                    val match = beaconMatch ?: macMatch
                    FriendWithProximity(
                        friend = it,
                        activeDetection = match,
                        matchedBy = when {
                            beaconMatch != null -> "BEACON_ID"
                            macMatch != null -> "MAC_ADDRESS"
                            else -> null
                        },
                        isBlocked = false
                    )
                }
            }
        }
    }

    fun observeSightingsForFriend(friendId: Long): Flow<List<SightingEntity>> =
        sightingDao.getSightingsForFriend(friendId)

    private fun recordSightingIfNeeded(
        friend: Friend,
        peer: DetectedPeer,
        matchedBy: String
    ) {
        val now = System.currentTimeMillis()
        val prev = lastSightingLoggedAt[friend.id] ?: 0L
        if (now - prev < SIGHTING_DEBOUNCE_MS) return
        lastSightingLoggedAt[friend.id] = now

        scope.launch {
            friendDao.updateProximityTimestamp(
                id = friend.id,
                timestamp = now,
                distanceMeters = peer.estimatedMeters,
                rssi = peer.rssi
            )
            sightingDao.insertSighting(
                SightingEntity(
                    friendId = friend.id,
                    friendName = friend.displayLabel,
                    matchedBy = matchedBy,
                    rssi = peer.rssi,
                    estimatedMeters = peer.estimatedMeters,
                    timestamp = now
                )
            )
        }
    }

    suspend fun findFriendByIdentifier(identifier: String): Friend? =
        friendDao.findFriendByDeviceIdentifier(identifier)

    suspend fun saveFriend(
        editingFriendId: Long?,
        name: String,
        nickname: String = "",
        beaconId: String,
        macAddress: String,
        note: String,
        avatarColorHex: Long,
        isFavorite: Boolean
    ): FriendFormValidation {
        val existing = friendDao.getAllFriendsSnapshot()
        val validation = IdentifierValidator.validateFriendForm(
            name = name,
            nickname = nickname,
            beaconId = beaconId,
            macAddress = macAddress,
            existingFriends = existing,
            editingFriendId = editingFriendId
        )
        if (!validation.isValid) return validation

        if (editingFriendId == null) {
            friendDao.insertFriend(
                Friend(
                    name = validation.normalizedName,
                    nickname = validation.normalizedNickname,
                    deviceIdentifier = validation.primaryDeviceIdentifier,
                    beaconId = validation.normalizedBeaconId,
                    macAddress = validation.normalizedMac,
                    note = note.trim(),
                    avatarColorHex = avatarColorHex,
                    isFavorite = isFavorite
                )
            )
        } else {
            val current = friendDao.getFriendById(editingFriendId)
            if (current != null) {
                friendDao.updateFriend(
                    current.copy(
                        name = validation.normalizedName,
                        nickname = validation.normalizedNickname,
                        deviceIdentifier = validation.primaryDeviceIdentifier,
                        beaconId = validation.normalizedBeaconId,
                        macAddress = validation.normalizedMac,
                        note = note.trim(),
                        avatarColorHex = avatarColorHex,
                        isFavorite = isFavorite
                    )
                )
            }
        }
        return validation
    }

    suspend fun blockPeer(peer: DetectedPeer) {
        val identifiers = listOf(peer.beaconId, peer.macAddress)
            .map { it.trim().uppercase(Locale.US) }
            .filter { it.isNotBlank() }
            .distinct()
        val label = peer.advertisedName.ifBlank {
            if (peer.isWhoIsNearMeApp) "WHOisnearME Peer" else "Bluetooth LE Device"
        }
        identifiers.forEach { id ->
            blockedDeviceDao.blockDevice(BlockedDevice(identifier = id, label = label))
        }
    }

    suspend fun blockFriend(friend: Friend) {
        val identifiers = listOf(friend.deviceIdentifier, friend.beaconId, friend.macAddress)
            .map { it.trim().uppercase(Locale.US) }
            .filter { it.isNotBlank() }
            .distinct()
        identifiers.forEach { id ->
            blockedDeviceDao.blockDevice(
                BlockedDevice(identifier = id, label = friend.displayLabel)
            )
        }
    }

    suspend fun unblockFriend(friend: Friend) {
        listOf(friend.deviceIdentifier, friend.beaconId, friend.macAddress)
            .map { it.trim().uppercase(Locale.US) }
            .filter { it.isNotBlank() }
            .distinct()
            .forEach { id ->
                blockedDeviceDao.unblockByIdentifier(id)
            }
    }

    suspend fun blockIdentifierManual(rawIdentifier: String, label: String): String? {
        val trimmed = rawIdentifier.trim()
        if (trimmed.isEmpty()) {
            return "Enter a Beacon ID or Bluetooth MAC address to block."
        }
        val normalized = when {
            IdentifierValidator.isValidBeaconId(trimmed) -> IdentifierValidator.normalizeBeaconId(trimmed)
            IdentifierValidator.isValidMacAddress(trimmed) -> IdentifierValidator.normalizeMacAddress(trimmed)
            else -> return "Use 8-hex Beacon ID (e.g. A7F2-9C14) or MAC (AA:BB:CC:DD:EE:FF)."
        }
        blockedDeviceDao.blockDevice(
            BlockedDevice(
                identifier = normalized,
                label = label.trim().ifBlank { "Manually blocked device" }
            )
        )
        return null
    }

    suspend fun unblockDevice(id: Long) {
        blockedDeviceDao.unblockById(id)
    }

    suspend fun toggleFavorite(friendId: Long, isFavorite: Boolean) {
        friendDao.setFavorite(friendId, isFavorite)
    }

    suspend fun deleteFriend(friendId: Long) {
        friendDao.deleteFriendById(friendId)
        lastSightingLoggedAt.remove(friendId)
    }

    suspend fun clearSightingsHistory() {
        sightingDao.clearAllSightings()
    }

    suspend fun clearAllLocalData() {
        sightingDao.clearAllSightings()
        friendDao.deleteAllFriends()
        blockedDeviceDao.clearAllBlockedDevices()
        lastSightingLoggedAt.clear()
    }

    suspend fun ensureBeaconIdInitialized(): String =
        preferencesRepository.ensureBeaconIdInitialized()

    suspend fun regenerateBeaconId(): String =
        preferencesRepository.regenerateBeaconId()

    suspend fun updateDisplayName(name: String) =
        preferencesRepository.updateDisplayName(name)

    suspend fun setAutoScanOnLaunch(enabled: Boolean) =
        preferencesRepository.setAutoScanOnLaunch(enabled)

    suspend fun setBroadcastEnabled(enabled: Boolean) =
        preferencesRepository.setBroadcastEnabled(enabled)

    suspend fun setAlertRangeMeters(meters: Float) =
        preferencesRepository.setAlertRangeMeters(meters)

    suspend fun setShowUnknownBleDevices(show: Boolean) =
        preferencesRepository.setShowUnknownBleDevices(show)

    suspend fun setBackgroundServiceEnabled(enabled: Boolean) =
        preferencesRepository.setBackgroundServiceEnabled(enabled)

    suspend fun setProximityNotificationsEnabled(enabled: Boolean) =
        preferencesRepository.setProximityNotificationsEnabled(enabled)

    suspend fun setHapticFeedbackEnabled(enabled: Boolean) =
        preferencesRepository.setHapticFeedbackEnabled(enabled)

    companion object {
        private const val SIGHTING_DEBOUNCE_MS = 30_000L

        fun isPeerBlocked(peer: DetectedPeer, blockedSet: Set<String>): Boolean {
            if (blockedSet.isEmpty()) return false
            val beaconBlocked = peer.beaconId.isNotBlank() &&
                blockedSet.contains(peer.beaconId.uppercase(Locale.US))
            val macBlocked = peer.macAddress.isNotBlank() &&
                blockedSet.contains(peer.macAddress.uppercase(Locale.US))
            return beaconBlocked || macBlocked
        }

        fun isFriendBlocked(friend: Friend, blockedSet: Set<String>): Boolean {
            if (blockedSet.isEmpty()) return false
            return listOf(friend.deviceIdentifier, friend.beaconId, friend.macAddress)
                .any { it.isNotBlank() && blockedSet.contains(it.uppercase(Locale.US)) }
        }
    }
}
