package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.BleRadioState
import com.example.data.model.DetectedPeer
import com.example.data.model.ProximityMath
import com.example.ui.components.FriendProximityCard
import com.example.ui.components.ProximityRadarView
import com.example.ui.components.RadioStatusBanner
import com.example.ui.components.buildRadarBlips
import com.example.ui.theme.MonoLabelStyle
import com.example.ui.viewmodel.WhoIsNearMeUiState

@Composable
fun RadarScreen(
    uiState: WhoIsNearMeUiState,
    onToggleScan: () -> Unit,
    onToggleBroadcast: (Boolean) -> Unit,
    onToggleBackgroundService: (Boolean) -> Unit,
    onRequestPermissions: () -> Unit,
    onRequestEnableBluetooth: () -> Unit,
    onDismissBleError: () -> Unit,
    onOpenFriendDetail: (Long) -> Unit,
    onOpenAddFriend: (DetectedPeer?) -> Unit,
    onBlockPeer: (DetectedPeer) -> Unit,
    onToggleFavorite: (com.example.data.local.entity.Friend) -> Unit,
    onNavigateToMyDevice: () -> Unit,
    onOpenVisualizer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val radarBlips = remember(uiState.friendsWithProximity) {
        buildRadarBlips(
            friends = uiState.friendsWithProximity,
            includeRecentRoomSightings = true
        )
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
                    .widthIn(max = 720.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Offline Bluetooth LE friend radar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        onClick = onNavigateToMyDevice,
                        modifier = Modifier.testTag("header_my_beacon_pill")
                    ) {
                        Text(
                            text = uiState.preferences.myBeaconId.ifBlank { "----" },
                            style = MonoLabelStyle,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                // Actionable permission / Bluetooth / error banner
                RadioStatusBanner(
                    radioState = uiState.radioState,
                    bleError = uiState.bleError,
                    onActionClick = {
                        when (uiState.radioState) {
                            BleRadioState.PERMISSION_REQUIRED -> onRequestPermissions()
                            BleRadioState.BLUETOOTH_OFF -> onRequestEnableBluetooth()
                            else -> {}
                        }
                    },
                    onDismissError = onDismissBleError
                )

                // Radar Visualizer
                ProximityRadarView(
                    blips = radarBlips,
                    isScanning = uiState.isScanning,
                    isAdvertising = uiState.isAdvertising,
                    alertRangeMeters = uiState.preferences.alertRangeMeters,
                    centerDeviceLabel = uiState.preferences.displayName,
                    onFriendClick = onOpenFriendDetail,
                    onExpandClick = onOpenVisualizer
                )

                // Control Deck Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onToggleScan,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("scan_toggle_button"),
                                colors = if (uiState.isScanning) {
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                } else {
                                    ButtonDefaults.buttonColors()
                                }
                            ) {
                                Icon(
                                    imageVector = if (uiState.isScanning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (uiState.isScanning) "Stop Scan" else "Start Scan")
                            }

                            FilledTonalButton(
                                onClick = { onOpenAddFriend(null) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("radar_add_friend_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PersonAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.WifiTethering,
                                    contentDescription = null,
                                    tint = if (uiState.isAdvertising) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Broadcast My Presence",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = if (uiState.isAdvertising) {
                                            "Visible to friends scanning nearby"
                                        } else {
                                            "Let friends with your Beacon ID detect you"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = uiState.preferences.broadcastEnabled,
                                onCheckedChange = onToggleBroadcast,
                                modifier = Modifier.testTag("broadcast_toggle_switch")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.NotificationsActive,
                                    contentDescription = null,
                                    tint = if (uiState.isBackgroundServiceRunning) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Background Discovery Service",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = if (uiState.isBackgroundServiceRunning) {
                                            "Running • Auto-saving WHOisnearME friends"
                                        } else {
                                            "Discover & save WHOisnearME peers in background"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = uiState.isBackgroundServiceRunning,
                                onCheckedChange = onToggleBackgroundService,
                                modifier = Modifier.testTag("background_service_switch")
                            )
                        }
                    }
                }
            }
        }

        // Nearby Friends Section Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Friends Near You (${uiState.nearbyFriends.size})",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "${uiState.friendsWithProximity.size} saved",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (uiState.nearbyFriends.isEmpty()) {
            item {
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_radar_empty),
                            contentDescription = "Proximity radar waiting for friends",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(148.dp)
                                .clip(MaterialTheme.shapes.medium)
                        )
                        Text(
                            text = if (uiState.friendsWithProximity.isEmpty()) {
                                stringResource(R.string.empty_friends_title)
                            } else {
                                stringResource(R.string.empty_radar_title)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (uiState.friendsWithProximity.isEmpty()) {
                                stringResource(R.string.empty_friends_subtitle)
                            } else {
                                stringResource(R.string.empty_radar_subtitle)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (uiState.friendsWithProximity.isEmpty()) {
                            Button(
                                onClick = { onOpenAddFriend(null) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("empty_add_friend_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Your First Friend")
                            }
                        }
                    }
                }
            }
        } else {
            items(uiState.nearbyFriends, key = { "nearby_${it.friend.id}" }) { item ->
                FriendProximityCard(
                    item = item,
                    onClick = { onOpenFriendDetail(item.friend.id) },
                    onToggleFavorite = { onToggleFavorite(item.friend) },
                    modifier = Modifier.widthIn(max = 720.dp)
                )
            }
        }

        // Discovered Unpaired Nearby Peers
        if (uiState.unmatchedNearbyPeers.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                        .padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Discovered Nearby Devices (${uiState.unmatchedNearbyPeers.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Tap + to save a nearby friend's Beacon ID or Bluetooth address locally.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(uiState.unmatchedNearbyPeers.take(15), key = { it.key }) { peer ->
                DiscoveredPeerRow(
                    peer = peer,
                    onQuickAdd = { onOpenAddFriend(peer) },
                    onBlock = { onBlockPeer(peer) },
                    modifier = Modifier.widthIn(max = 720.dp)
                )
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun DiscoveredPeerRow(
    peer: DetectedPeer,
    onQuickAdd: () -> Unit,
    onBlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (peer.isWhoIsNearMeApp) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.BluetoothSearching,
                contentDescription = null,
                tint = if (peer.isWhoIsNearMeApp) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        peer.isWhoIsNearMeApp && peer.advertisedName.isNotBlank() ->
                            "${peer.advertisedName} (WHOisnearME)"
                        peer.isWhoIsNearMeApp -> "WHOisnearME Peer"
                        peer.advertisedName.isNotBlank() -> peer.advertisedName
                        else -> "Bluetooth LE Device"
                    },
                    style = MaterialTheme.typography.titleSmall
                )
                val idLine = buildString {
                    if (peer.beaconId.isNotBlank()) append("ID ${peer.beaconId} • ")
                    append(peer.macAddress)
                    append(" • ${ProximityMath.formatDistance(peer.estimatedMeters)} (${peer.rssi} dBm)")
                }
                Text(
                    text = idLine,
                    style = MonoLabelStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onBlock,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("block_peer_${peer.key}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Block,
                    contentDescription = "Block device identifier",
                    tint = MaterialTheme.colorScheme.error
                )
            }
            FilledTonalButton(
                onClick = onQuickAdd,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("quick_add_peer_${peer.key}")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save")
            }
        }
    }
}
