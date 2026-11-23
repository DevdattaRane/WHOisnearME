package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ble.WhoIsNearMeScannerService
import com.example.data.local.entity.BlockedDevice
import com.example.data.local.entity.Friend
import com.example.data.local.entity.SightingEntity
import java.util.Locale
import com.example.data.model.BleRadioState
import com.example.data.model.DetectedPeer
import com.example.data.model.FriendFormValidation
import com.example.data.model.FriendWithProximity
import com.example.data.notifications.ProximityNotificationService
import com.example.data.preferences.UserPreferences
import com.example.data.repository.FriendRepository
import com.example.ui.theme.FriendAvatarColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FriendFilter(val label: String) {
    ALL("All"),
    NEARBY("Nearby"),
    FAVORITES("Favorites")
}

data class WhoIsNearMeUiState(
    val isReady: Boolean = false,
    val friendsWithProximity: List<FriendWithProximity> = emptyList(),
    val nearbyFriends: List<FriendWithProximity> = emptyList(),
    val unmatchedNearbyPeers: List<DetectedPeer> = emptyList(),
    val recentSightings: List<SightingEntity> = emptyList(),
    val blockedDevices: List<BlockedDevice> = emptyList(),
    val preferences: UserPreferences = UserPreferences(
        displayName = "My Phone",
        myBeaconId = "",
        autoScanOnLaunch = true,
        broadcastEnabled = true,
        alertRangeMeters = 10f,
        showUnknownBleDevices = true
    ),
    val radioState: BleRadioState = BleRadioState.PERMISSION_REQUIRED,
    val isScanning: Boolean = false,
    val isAdvertising: Boolean = false,
    val isBackgroundServiceRunning: Boolean = false,
    val bleError: String? = null,
    val searchQuery: String = "",
    val friendFilter: FriendFilter = FriendFilter.ALL,
    val snackbarMessage: String? = null
)

data class FriendFormState(
    val isOpen: Boolean = false,
    val editingFriendId: Long? = null,
    val name: String = "",
    val nickname: String = "",
    val beaconId: String = "",
    val macAddress: String = "",
    val note: String = "",
    val avatarColorHex: Long = FriendAvatarColors.first(),
    val isFavorite: Boolean = false,
    val nameError: String? = null,
    val beaconIdError: String? = null,
    val macError: String? = null,
    val generalError: String? = null,
    val isSaving: Boolean = false
)

class MainViewModel(
    private val repository: FriendRepository,
    private val proximityNotificationService: ProximityNotificationService
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _friendFilter = MutableStateFlow(FriendFilter.ALL)
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    private val _isReady = MutableStateFlow(false)

    private val _formState = MutableStateFlow(FriendFormState())
    val formState: StateFlow<FriendFormState> = _formState.asStateFlow()

    private val bleCombinedFlow = combine(
        repository.bleManager.radioState,
        repository.bleManager.isScanning,
        repository.bleManager.isAdvertising,
        repository.bleManager.statusError,
        repository.bleManager.detectedPeers
    ) { radioState, isScanning, isAdvertising, statusError, detectedPeers ->
        BleSnapshot(radioState, isScanning, isAdvertising, statusError, detectedPeers)
    }

    private val serviceCombinedFlow = combine(
        WhoIsNearMeScannerService.isRunning,
        WhoIsNearMeScannerService.lastServiceMessage
    ) { running, serviceMsg ->
        running to serviceMsg
    }

    private val dataCombinedFlow = combine(
        repository.friendsWithProximityFlow,
        repository.sightingsFlow,
        repository.blockedDevicesFlow,
        repository.userPreferencesFlow,
        _isReady
    ) { friendsWithProximity, sightings, blockedDevices, prefs, ready ->
        DataSnapshot(friendsWithProximity, sightings, blockedDevices, prefs, ready)
    }

    private val queryFilterFlow = combine(
        _searchQuery,
        _friendFilter,
        _snackbarMessage
    ) { query, filter, snackbar ->
        Triple(query, filter, snackbar)
    }

    val uiState: StateFlow<WhoIsNearMeUiState> = combine(
        dataCombinedFlow,
        bleCombinedFlow,
        serviceCombinedFlow,
        queryFilterFlow
    ) { data, ble, serviceInfo, queryFilter ->
        val (serviceRunning, serviceMsg) = serviceInfo
        val (query, filter, snackbar) = queryFilter
        val blockedSet = data.blockedDevices
            .map { it.identifier.uppercase(Locale.US) }
            .toSet()
        val nearbyFriends = data.friendsWithProximity.filter { it.isCurrentlyNearby && !it.isBlocked }
        val matchedKeys = data.friendsWithProximity.mapNotNull { it.activeDetection?.key }.toSet()
        val unmatchedPeers = ble.detectedPeers.filter { peer ->
            peer.key !in matchedKeys &&
                !FriendRepository.isPeerBlocked(peer, blockedSet) &&
                (peer.isWhoIsNearMeApp || data.prefs.showUnknownBleDevices)
        }

        WhoIsNearMeUiState(
            isReady = data.isReady,
            friendsWithProximity = data.friendsWithProximity,
            nearbyFriends = nearbyFriends,
            unmatchedNearbyPeers = unmatchedPeers,
            recentSightings = data.sightings,
            blockedDevices = data.blockedDevices,
            preferences = data.prefs,
            radioState = ble.radioState,
            isScanning = ble.isScanning || serviceRunning,
            isAdvertising = ble.isAdvertising,
            isBackgroundServiceRunning = serviceRunning,
            bleError = ble.statusError ?: serviceMsg,
            searchQuery = query,
            friendFilter = filter,
            snackbarMessage = snackbar
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = WhoIsNearMeUiState()
    )

    init {
        viewModelScope.launch {
            val beaconId = repository.ensureBeaconIdInitialized()
            val prefs = repository.userPreferencesFlow.first()
            _isReady.value = true
            val radioState = repository.bleManager.refreshRadioState()
            if (radioState == BleRadioState.READY) {
                if (prefs.autoScanOnLaunch) {
                    repository.bleManager.startScanning()
                }
                if (prefs.broadcastEnabled) {
                    repository.bleManager.startAdvertising(beaconId, prefs.displayName)
                }
            }
        }

        // Trigger threshold-based local notifications and subtle haptics for live foreground detections
        viewModelScope.launch {
            uiState.collect { state ->
                if (state.isReady) {
                    proximityNotificationService.evaluateFriendDetections(
                        friendsWithProximity = state.friendsWithProximity,
                        unmatchedPeers = state.unmatchedNearbyPeers,
                        prefs = state.preferences
                    )
                }
            }
        }
    }

    fun requiredPermissions(): Array<String> = repository.bleManager.requiredPermissions()

    fun onPermissionsResult() {
        val state = repository.bleManager.refreshRadioState()
        if (state == BleRadioState.READY) {
            val prefs = uiState.value.preferences
            if (prefs.autoScanOnLaunch && !uiState.value.isScanning) {
                repository.bleManager.startScanning()
            }
            if (prefs.broadcastEnabled && prefs.myBeaconId.isNotBlank()) {
                repository.bleManager.startAdvertising(prefs.myBeaconId, prefs.displayName)
            }
            showSnackbar("Nearby permissions granted.")
        } else if (state == BleRadioState.PERMISSION_REQUIRED) {
            showSnackbar("Nearby device permission is needed to scan and broadcast.")
        }
    }

    fun onResumeRefresh() {
        repository.bleManager.refreshRadioState()
    }

    fun toggleScanning(context: Context? = null) {
        if (uiState.value.isBackgroundServiceRunning && context != null) {
            setBackgroundServiceEnabled(context, false)
            return
        }
        val ble = repository.bleManager
        val state = ble.refreshRadioState()
        if (state != BleRadioState.READY) {
            showSnackbar(
                when (state) {
                    BleRadioState.PERMISSION_REQUIRED -> "Grant Nearby permission first to scan."
                    BleRadioState.BLUETOOTH_OFF -> "Turn on Bluetooth to start scanning."
                    BleRadioState.BLE_UNAVAILABLE -> "Bluetooth LE is unavailable on this device."
                    BleRadioState.READY -> ""
                }
            )
            return
        }
        if (uiState.value.isScanning) {
            ble.stopScanning()
        } else {
            ble.startScanning()
        }
    }

    fun setBroadcastEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setBroadcastEnabled(enabled)
            val ble = repository.bleManager
            if (!enabled) {
                ble.stopAdvertising()
            } else {
                val state = ble.refreshRadioState()
                if (state == BleRadioState.READY) {
                    val prefs = uiState.value.preferences
                    ble.startAdvertising(prefs.myBeaconId, prefs.displayName)
                }
            }
        }
    }

    fun setAutoScanOnLaunch(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoScanOnLaunch(enabled)
        }
    }

    fun setAlertRangeMeters(meters: Float) {
        viewModelScope.launch {
            repository.setAlertRangeMeters(meters)
        }
    }

    fun setShowUnknownBleDevices(show: Boolean) {
        viewModelScope.launch {
            repository.setShowUnknownBleDevices(show)
        }
    }

    fun setProximityNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setProximityNotificationsEnabled(enabled)
        }
    }

    fun setHapticFeedbackEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setHapticFeedbackEnabled(enabled)
            if (enabled) {
                proximityNotificationService.triggerSubtleHapticPulse()
            }
        }
    }

    fun sendTestProximityAlert() {
        val prefs = uiState.value.preferences
        val sent = proximityNotificationService.sendTestAlert(
            thresholdMeters = prefs.alertRangeMeters,
            enableNotification = prefs.proximityNotificationsEnabled,
            enableHaptics = prefs.hapticFeedbackEnabled
        )
        showSnackbar(
            if (sent) {
                "Triggered proximity alert feedback (${prefs.alertRangeMeters.toInt()}m threshold)."
            } else {
                "Enable notifications or haptic feedback first to test alerts."
            }
        )
    }

    fun setBackgroundServiceEnabled(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled) {
                repository.setBackgroundServiceEnabled(false)
                WhoIsNearMeScannerService.stop(context)
                showSnackbar("Background discovery service stopped.")
                return@launch
            }

            val state = repository.bleManager.refreshRadioState()
            if (state != BleRadioState.READY) {
                showSnackbar(
                    when (state) {
                        BleRadioState.PERMISSION_REQUIRED -> "Grant Nearby permission before starting background service."
                        BleRadioState.BLUETOOTH_OFF -> "Turn on Bluetooth before starting background service."
                        BleRadioState.BLE_UNAVAILABLE -> "Bluetooth LE is unavailable on this device."
                        BleRadioState.READY -> ""
                    }
                )
                return@launch
            }

            repository.setBackgroundServiceEnabled(true)
            // Hand off BLE scanning cleanly to the background foreground service
            repository.bleManager.stopScanning()
            WhoIsNearMeScannerService.start(context)
            showSnackbar("Background discovery service started.")
        }
    }

    fun updateDisplayName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            showSnackbar("Display name cannot be empty.")
            return
        }
        viewModelScope.launch {
            repository.updateDisplayName(trimmed)
            if (uiState.value.isAdvertising) {
                val beaconId = uiState.value.preferences.myBeaconId
                repository.bleManager.startAdvertising(beaconId, trimmed)
            }
            showSnackbar("Device display name saved.")
        }
    }

    fun regenerateBeaconId() {
        viewModelScope.launch {
            val newId = repository.regenerateBeaconId()
            if (uiState.value.isAdvertising) {
                repository.bleManager.startAdvertising(newId, uiState.value.preferences.displayName)
            }
            showSnackbar("New Beacon ID generated: $newId")
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateFriendFilter(filter: FriendFilter) {
        _friendFilter.value = filter
    }

    fun openAddFriendForm(prefillPeer: DetectedPeer? = null) {
        _formState.value = FriendFormState(
            isOpen = true,
            editingFriendId = null,
            name = prefillPeer?.advertisedName?.takeIf { it.isNotBlank() } ?: "",
            nickname = "",
            beaconId = prefillPeer?.beaconId ?: "",
            macAddress = prefillPeer?.macAddress ?: "",
            note = if (prefillPeer != null) "Added from nearby radar" else "",
            avatarColorHex = FriendAvatarColors.random(),
            isFavorite = false
        )
    }

    fun openEditFriendForm(friend: Friend) {
        _formState.value = FriendFormState(
            isOpen = true,
            editingFriendId = friend.id,
            name = friend.name,
            nickname = friend.nickname,
            beaconId = friend.beaconId,
            macAddress = friend.macAddress,
            note = friend.note,
            avatarColorHex = friend.avatarColorHex,
            isFavorite = friend.isFavorite
        )
    }

    fun closeFriendForm() {
        _formState.value = FriendFormState(isOpen = false)
    }

    fun updateFormName(value: String) {
        _formState.update { it.copy(name = value, nameError = null, generalError = null) }
    }

    fun updateFormNickname(value: String) {
        _formState.update { it.copy(nickname = value) }
    }

    fun updateFormBeaconId(value: String) {
        _formState.update { it.copy(beaconId = value, beaconIdError = null, generalError = null) }
    }

    fun updateFormMac(value: String) {
        _formState.update { it.copy(macAddress = value, macError = null, generalError = null) }
    }

    fun updateFormNote(value: String) {
        _formState.update { it.copy(note = value) }
    }

    fun updateFormAvatarColor(colorHex: Long) {
        _formState.update { it.copy(avatarColorHex = colorHex) }
    }

    fun updateFormFavorite(isFav: Boolean) {
        _formState.update { it.copy(isFavorite = isFav) }
    }

    fun prefillFormFromPeer(peer: DetectedPeer) {
        _formState.update { current ->
            current.copy(
                name = current.name.ifBlank { peer.advertisedName },
                beaconId = peer.beaconId.ifBlank { current.beaconId },
                macAddress = peer.macAddress.ifBlank { current.macAddress },
                beaconIdError = null,
                macError = null,
                generalError = null
            )
        }
    }

    fun submitFriendForm() {
        val current = _formState.value
        if (current.isSaving) return
        _formState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val result: FriendFormValidation = repository.saveFriend(
                editingFriendId = current.editingFriendId,
                name = current.name,
                nickname = current.nickname,
                beaconId = current.beaconId,
                macAddress = current.macAddress,
                note = current.note,
                avatarColorHex = current.avatarColorHex,
                isFavorite = current.isFavorite
            )

            if (result.isValid) {
                val label = if (current.editingFriendId == null) "Friend added" else "Friend updated"
                _formState.value = FriendFormState(isOpen = false)
                showSnackbar("$label: ${result.normalizedNickname.ifBlank { result.normalizedName }}")
            } else {
                _formState.update {
                    it.copy(
                        isSaving = false,
                        nameError = result.nameError,
                        beaconIdError = result.beaconIdError,
                        macError = result.macError,
                        generalError = result.generalError
                    )
                }
            }
        }
    }

    fun toggleFavorite(friend: Friend) {
        viewModelScope.launch {
            repository.toggleFavorite(friend.id, !friend.isFavorite)
        }
    }

    fun deleteFriend(friend: Friend) {
        viewModelScope.launch {
            repository.deleteFriend(friend.id)
            showSnackbar("Removed ${friend.displayLabel} from saved friends.")
        }
    }

    fun blockPeer(peer: DetectedPeer) {
        viewModelScope.launch {
            repository.blockPeer(peer)
            val idLabel = peer.beaconId.ifBlank { peer.macAddress }
            showSnackbar("Blocked $idLabel from appearing on radar.")
        }
    }

    fun blockFriend(friend: Friend) {
        viewModelScope.launch {
            repository.blockFriend(friend)
            showSnackbar("Blocked ${friend.displayLabel} from radar.")
        }
    }

    fun unblockFriend(friend: Friend) {
        viewModelScope.launch {
            repository.unblockFriend(friend)
            showSnackbar("Unblocked ${friend.displayLabel}.")
        }
    }

    fun blockIdentifierManual(
        identifier: String,
        label: String,
        onValidationError: (String) -> Unit,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val error = repository.blockIdentifierManual(identifier, label)
            if (error != null) {
                onValidationError(error)
            } else {
                onSuccess()
                showSnackbar("Blocked device identifier.")
            }
        }
    }

    fun unblockDevice(device: BlockedDevice) {
        viewModelScope.launch {
            repository.unblockDevice(device.id)
            showSnackbar("Unblocked ${device.identifier}.")
        }
    }

    fun clearSightingsHistory() {
        viewModelScope.launch {
            repository.clearSightingsHistory()
            showSnackbar("Proximity history cleared.")
        }
    }

    fun clearAllLocalData() {
        viewModelScope.launch {
            repository.clearAllLocalData()
            showSnackbar("All saved friends and history deleted.")
        }
    }

    fun observeFriendDetail(friendId: Long): Flow<FriendWithProximity?> =
        repository.observeFriendDetail(friendId)

    fun observeSightingsForFriend(friendId: Long): Flow<List<SightingEntity>> =
        repository.observeSightingsForFriend(friendId)

    fun clearBleError() {
        repository.bleManager.clearError()
    }

    fun showSnackbar(message: String) {
        _snackbarMessage.value = message
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        repository.bleManager.release()
    }

    private data class BleSnapshot(
        val radioState: BleRadioState,
        val isScanning: Boolean,
        val isAdvertising: Boolean,
        val statusError: String?,
        val detectedPeers: List<DetectedPeer>
    )

    private data class DataSnapshot(
        val friendsWithProximity: List<FriendWithProximity>,
        val sightings: List<SightingEntity>,
        val blockedDevices: List<BlockedDevice>,
        val prefs: UserPreferences,
        val isReady: Boolean
    )

    class Factory(
        private val repository: FriendRepository,
        private val proximityNotificationService: ProximityNotificationService
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(repository, proximityNotificationService) as T
        }
    }
}
