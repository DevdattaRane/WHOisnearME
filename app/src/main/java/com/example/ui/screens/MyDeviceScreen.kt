package com.example.ui.screens

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ble.BleProximityManager
import com.example.ui.theme.MonoHeroStyle
import com.example.ui.theme.MonoLabelStyle
import com.example.ui.viewmodel.WhoIsNearMeUiState

@Composable
fun MyDeviceScreen(
    uiState: WhoIsNearMeUiState,
    onUpdateDisplayName: (String) -> Unit,
    onRegenerateBeaconId: () -> Unit,
    onToggleBroadcast: (Boolean) -> Unit,
    onCopiedMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val prefs = uiState.preferences

    var nameInput by remember { mutableStateOf(prefs.displayName) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var showRegenerateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(prefs.displayName) {
        nameInput = prefs.displayName
    }

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
                    text = stringResource(R.string.my_device_title),
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Share this ID with friends so their WHOisnearME radar detects your device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Hero Beacon ID Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WifiTethering,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "MY BEACON ID",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = prefs.myBeaconId.ifBlank { "GENERATING..." },
                            style = MonoHeroStyle,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp)
                        )
                    }

                    Text(
                        text = "Device: ${Build.MANUFACTURER} ${Build.MODEL}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (prefs.myBeaconId.isNotBlank()) {
                                    clipboardManager.setText(AnnotatedString(prefs.myBeaconId))
                                    onCopiedMessage("Copied Beacon ID: ${prefs.myBeaconId}")
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("copy_beacon_id_button")
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy ID")
                        }

                        FilledTonalButton(
                            onClick = {
                                if (prefs.myBeaconId.isNotBlank()) {
                                    val shareText = "Add me on WHOisnearME!\nName: ${prefs.displayName}\nBeacon ID: ${prefs.myBeaconId}"
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(
                                            intent,
                                            context.getString(R.string.share_beacon_chooser)
                                        )
                                    )
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("share_beacon_id_button")
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share")
                        }
                    }

                    TextButton(
                        onClick = { showRegenerateDialog = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("regenerate_beacon_button")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Rotate Beacon ID")
                    }
                }
            }
        }

        // Display Name & Broadcast Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Broadcast Identity",
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = {
                            nameInput = it
                            nameError = null
                        },
                        label = { Text("My Display Name") },
                        supportingText = {
                            Text(nameError ?: "Sent in your short BLE packet (up to 8 UTF-8 bytes displayed nearby)")
                        },
                        isError = nameError != null,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("my_display_name_input")
                    )

                    Button(
                        onClick = {
                            val trimmed = nameInput.trim()
                            if (trimmed.isEmpty()) {
                                nameError = "Enter a display name."
                            } else if (trimmed.length > 24) {
                                nameError = "Max 24 characters."
                            } else {
                                onUpdateDisplayName(trimmed)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("save_display_name_button")
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Display Name")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Broadcast Presence",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (uiState.isAdvertising) "Currently advertising via BLE" else "Advertising paused",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = prefs.broadcastEnabled,
                            onCheckedChange = onToggleBroadcast,
                            modifier = Modifier.testTag("my_device_broadcast_switch")
                        )
                    }
                }
            }
        }

        // How MAC Address & BLE Beacon Matching Works
        item {
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "About MAC Addresses on Android",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Text(
                        text = "Modern Android (6.0+) hides a phone's factory Wi-Fi and Bluetooth MAC address (returning 02:00:00:00:00:00) and rotates BLE MACs for privacy. WHOisnearME works reliably without servers by broadcasting your persistent Beacon ID over a dedicated Bluetooth LE service UUID—and also supports matching a friend's visible Bluetooth MAC address if you enter it manually.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Service UUID: ${BleProximityManager.SERVICE_UUID}",
                        style = MonoLabelStyle,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    if (showRegenerateDialog) {
        AlertDialog(
            onDismissRequest = { showRegenerateDialog = false },
            title = { Text("Rotate your Beacon ID?") },
            text = {
                Text("Friends who already saved your old Beacon ID will need your new ID to detect you unless they also have your Bluetooth MAC saved.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRegenerateDialog = false
                        onRegenerateBeaconId()
                    },
                    modifier = Modifier.testTag("confirm_regenerate_beacon_button")
                ) {
                    Text("Rotate ID")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegenerateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
