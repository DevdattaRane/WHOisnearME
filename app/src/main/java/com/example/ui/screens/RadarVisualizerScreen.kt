package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.ProximityMath
import com.example.ui.components.FriendAvatar
import com.example.ui.components.ProximityRadarView
import com.example.ui.components.ProximityZoneBadge
import com.example.ui.components.buildRadarBlips
import com.example.ui.components.formatRelativeTimestamp
import com.example.ui.theme.MonoLabelStyle
import com.example.ui.viewmodel.WhoIsNearMeUiState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarVisualizerScreen(
    uiState: WhoIsNearMeUiState,
    onBack: () -> Unit,
    onToggleScan: () -> Unit,
    onAlertRangeChange: (Float) -> Unit,
    onOpenFriendDetail: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var includeRecentRoom by remember { mutableStateOf(true) }
    var selectedFriendId by remember { mutableStateOf<Long?>(null) }

    val blips = remember(uiState.friendsWithProximity, includeRecentRoom) {
        buildRadarBlips(
            friends = uiState.friendsWithProximity,
            includeRecentRoomSightings = includeRecentRoom
        )
    }

    val selectedFriend = remember(uiState.friendsWithProximity, selectedFriendId, blips) {
        val targetId = selectedFriendId ?: blips.firstOrNull()?.friendId
        uiState.friendsWithProximity.firstOrNull { it.friend.id == targetId && !it.isBlocked }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Radar Visualizer") },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("visualizer_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Navigate back")
                }
            },
            actions = {
                IconButton(
                    onClick = onToggleScan,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("visualizer_scan_toggle")
                ) {
                    Icon(
                        imageVector = if (uiState.isScanning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = if (uiState.isScanning) "Stop scanning" else "Start scanning",
                        tint = if (uiState.isScanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = !includeRecentRoom,
                        onClick = { includeRecentRoom = false },
                        label = { Text("Live Only (${uiState.nearbyFriends.size})") },
                        modifier = Modifier.testTag("visualizer_live_only_chip")
                    )
                    FilterChip(
                        selected = includeRecentRoom,
                        onClick = { includeRecentRoom = true },
                        label = { Text("Live + Recent Room (${blips.size})") },
                        modifier = Modifier.testTag("visualizer_recent_room_chip")
                    )
                }
            }

            item {
                ProximityRadarView(
                    blips = blips,
                    isScanning = uiState.isScanning,
                    isAdvertising = uiState.isAdvertising,
                    alertRangeMeters = uiState.preferences.alertRangeMeters,
                    centerDeviceLabel = uiState.preferences.displayName,
                    selectedFriendId = selectedFriend?.friend?.id,
                    onFriendClick = { friendId -> selectedFriendId = friendId },
                    modifier = Modifier.widthIn(max = 720.dp)
                )
            }

            if (blips.isNotEmpty()) {
                item {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(blips, key = { it.friendId }) { blip ->
                            FilterChip(
                                selected = selectedFriend?.friend?.id == blip.friendId,
                                onClick = { selectedFriendId = blip.friendId },
                                label = {
                                    Text(
                                        "${blip.label} • ${ProximityMath.formatDistance(blip.distanceMeters)}"
                                    )
                                }
                            )
                        }
                    }
                }
            }

            item {
                if (selectedFriend != null) {
                    val friend = selectedFriend.friend
                    val detection = selectedFriend.activeDetection
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FriendAvatar(
                                    name = friend.displayLabel,
                                    colorHex = friend.avatarColorHex,
                                    isOnline = detection != null,
                                    size = 48.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = friend.displayLabel,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = friend.deviceIdentifier.ifBlank {
                                            friend.beaconId.ifBlank { friend.macAddress }
                                        },
                                        style = MonoLabelStyle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (detection != null) {
                                    ProximityZoneBadge(
                                        zone = detection.zone,
                                        distanceMeters = detection.estimatedMeters
                                    )
                                }
                            }

                            Text(
                                text = if (detection != null) {
                                    "Live signal: ${detection.rssi} dBm • Distance ${ProximityMath.formatDistance(detection.estimatedMeters)}"
                                } else {
                                    "Last Room sighting: ${formatRelativeTimestamp(friend.lastSeenTimestamp)} • Distance ${
                                        ProximityMath.formatDistance(friend.lastSeenDistanceMeters ?: 10f)
                                    }"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Button(
                                onClick = { onOpenFriendDetail(friend.id) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("visualizer_inspect_friend_button")
                            ) {
                                Text("Inspect Friend Details")
                            }
                        }
                    }
                } else {
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = "No live or recent friend sightings in Room within the last 15 minutes. Keep scanning active while nearby friends broadcast WHOisnearME.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            item {
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Radial Scale",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "${uiState.preferences.alertRangeMeters.roundToInt()}m max",
                                style = MonoLabelStyle,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = uiState.preferences.alertRangeMeters,
                            onValueChange = onAlertRangeChange,
                            valueRange = 2f..25f,
                            steps = 22
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}
