package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.entity.BlockedDevice
import com.example.data.model.ProximityMath
import com.example.ui.components.formatExactTime
import com.example.ui.theme.MonoLabelStyle
import com.example.ui.viewmodel.WhoIsNearMeUiState
import kotlin.math.roundToInt

@Composable
fun HistorySettingsScreen(
    uiState: WhoIsNearMeUiState,
    onAutoScanToggle: (Boolean) -> Unit,
    onBroadcastToggle: (Boolean) -> Unit,
    onBackgroundServiceToggle: (Boolean) -> Unit,
    onProximityNotificationsToggle: (Boolean) -> Unit,
    onHapticFeedbackToggle: (Boolean) -> Unit,
    onTestProximityAlert: () -> Unit,
    onAlertRangeChange: (Float) -> Unit,
    onShowUnknownBleToggle: (Boolean) -> Unit,
    onOpenFriendDetail: (Long) -> Unit,
    onBlockIdentifierManual: (String, String, (String) -> Unit, () -> Unit) -> Unit,
    onUnblockDevice: (BlockedDevice) -> Unit,
    onClearHistory: () -> Unit,
    onResetAllData: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showResetAllDialog by remember { mutableStateOf(false) }
    var showManualBlockDialog by remember { mutableStateOf(false) }
    var blockIdInput by remember { mutableStateOf("") }
    var blockLabelInput by remember { mutableStateOf("") }
    var blockError by remember { mutableStateOf<String?>(null) }
    val prefs = uiState.preferences

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
            ) {
                Text(
                    text = stringResource(R.string.history_title),
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Offline radar preferences and proximity sightings log",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Settings Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Radar Preferences",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    SettingSwitchRow(
                        title = "Auto-Scan on App Launch",
                        subtitle = "Start listening for friends automatically when opening WHOisnearME",
                        checked = prefs.autoScanOnLaunch,
                        onCheckedChange = onAutoScanToggle,
                        testTag = "setting_autoscan_switch"
                    )

                    HorizontalDivider()

                    SettingSwitchRow(
                        title = "Broadcast My Presence",
                        subtitle = "Advertise your Beacon ID so nearby friends can locate you",
                        checked = prefs.broadcastEnabled,
                        onCheckedChange = onBroadcastToggle,
                        testTag = "setting_broadcast_switch"
                    )

                    HorizontalDivider()

                    SettingSwitchRow(
                        title = "Background Discovery Service",
                        subtitle = "Run foreground Bluetooth LE service to discover WHOisnearME peers and update Room",
                        checked = uiState.isBackgroundServiceRunning,
                        onCheckedChange = onBackgroundServiceToggle,
                        testTag = "setting_background_service_switch"
                    )

                    HorizontalDivider()

                    SettingSwitchRow(
                        title = "Proximity Notifications",
                        subtitle = "Post a local alert when a new friend enters your threshold distance",
                        checked = prefs.proximityNotificationsEnabled,
                        onCheckedChange = onProximityNotificationsToggle,
                        testTag = "setting_proximity_notifications_switch"
                    )

                    HorizontalDivider()

                    SettingSwitchRow(
                        title = "Subtle Haptic Feedback",
                        subtitle = "Trigger a gentle vibration pulse when a friend is detected within range",
                        checked = prefs.hapticFeedbackEnabled,
                        onCheckedChange = onHapticFeedbackToggle,
                        testTag = "setting_haptic_feedback_switch"
                    )

                    HorizontalDivider()

                    SettingSwitchRow(
                        title = "Show Unpaired Nearby BLE Devices",
                        subtitle = "List nearby Bluetooth LE devices on Radar so you can save their address",
                        checked = prefs.showUnknownBleDevices,
                        onCheckedChange = onShowUnknownBleToggle,
                        testTag = "setting_unknown_ble_switch"
                    )

                    HorizontalDivider()

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Alert Threshold & Radar Range",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "${prefs.alertRangeMeters.roundToInt()} meters",
                                style = MonoLabelStyle,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = prefs.alertRangeMeters,
                            onValueChange = onAlertRangeChange,
                            valueRange = 2f..25f,
                            steps = 22,
                            modifier = Modifier.testTag("setting_range_slider")
                        )
                        FilledTonalButton(
                            onClick = onTestProximityAlert,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("test_proximity_alert_button")
                        ) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Test Proximity Alert & Haptic")
                        }
                    }
                }
            }
        }

        // Blocked Identifiers Section
        item {
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Block,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Blocked Identifiers (${uiState.blockedDevices.size})",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                blockIdInput = ""
                                blockLabelInput = ""
                                blockError = null
                                showManualBlockDialog = true
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("open_block_manual_dialog_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Block ID")
                        }
                    }

                    if (uiState.blockedDevices.isEmpty()) {
                        Text(
                            text = "No blocked devices. Any Beacon ID or MAC address you block will be hidden from the Radar screen and ignored during scanning.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        uiState.blockedDevices.forEachIndexed { idx, device ->
                            if (idx > 0) HorizontalDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = device.identifier,
                                        style = MonoLabelStyle,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${device.label.ifBlank { "Blocked device" }} • ${formatExactTime(device.blockedAt)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(
                                    onClick = { onUnblockDevice(device) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("unblock_device_${device.id}")
                                ) {
                                    Text("Unblock")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Data Management Actions
        item {
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Local Storage Management",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showClearHistoryDialog = true },
                            enabled = uiState.recentSightings.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("clear_history_button")
                        ) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear Log")
                        }
                        OutlinedButton(
                            onClick = { showResetAllDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("reset_all_data_button")
                        ) {
                            Icon(
                                Icons.Filled.DeleteForever,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reset Data", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        // Recent Sightings Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Recent Sightings (${uiState.recentSightings.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        if (uiState.recentSightings.isEmpty()) {
            item {
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = "No proximity sightings logged yet. Detected encounters with your saved friends will appear here and stay saved offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(uiState.recentSightings, key = { it.id }) { sighting ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                        .clickable { onOpenFriendDetail(sighting.friendId) },
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sighting.friendName,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "${formatExactTime(sighting.timestamp)} • ${
                                    if (sighting.matchedBy == "BEACON_ID") "Beacon ID" else "MAC"
                                }",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = ProximityMath.formatDistance(sighting.estimatedMeters),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${sighting.rssi} dBm",
                                style = MonoLabelStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear proximity log?") },
            text = { Text("This removes all recorded sighting timestamps while keeping your saved friends.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearHistoryDialog = false
                        onClearHistory()
                    },
                    modifier = Modifier.testTag("confirm_clear_history_button")
                ) {
                    Text("Clear Log")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showManualBlockDialog) {
        AlertDialog(
            onDismissRequest = { showManualBlockDialog = false },
            title = { Text("Block Device Identifier") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter a Beacon ID (e.g. A7F2-9C14) or Bluetooth MAC (AA:BB:CC:DD:EE:FF) to hide it from the Radar screen.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = blockIdInput,
                        onValueChange = {
                            blockIdInput = it
                            blockError = null
                        },
                        label = { Text("Beacon ID or MAC *") },
                        placeholder = { Text("A7F2-9C14 or AA:BB:CC:DD:EE:FF") },
                        isError = blockError != null,
                        supportingText = blockError?.let { { Text(it) } },
                        singleLine = true,
                        textStyle = MonoLabelStyle,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_block_identifier_input")
                    )
                    OutlinedTextField(
                        value = blockLabelInput,
                        onValueChange = { blockLabelInput = it },
                        label = { Text("Label (Optional)") },
                        placeholder = { Text("e.g. Neighbor beacon") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_block_label_input")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onBlockIdentifierManual(
                            blockIdInput,
                            blockLabelInput,
                            { err -> blockError = err },
                            { showManualBlockDialog = false }
                        )
                    },
                    modifier = Modifier.testTag("confirm_manual_block_button")
                ) {
                    Text("Block", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualBlockDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showResetAllDialog) {
        AlertDialog(
            onDismissRequest = { showResetAllDialog = false },
            title = { Text("Delete all local data?") },
            text = { Text("This deletes all saved friends and proximity history stored on this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetAllDialog = false
                        onResetAllData()
                    },
                    modifier = Modifier.testTag("confirm_reset_all_button")
                ) {
                    Text("Delete All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
