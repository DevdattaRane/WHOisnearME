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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.data.local.entity.Friend
import com.example.data.local.entity.SightingEntity
import com.example.data.model.FriendWithProximity
import com.example.data.model.ProximityMath
import com.example.ui.components.FriendAvatar
import com.example.ui.components.ProximityZoneBadge
import com.example.ui.components.formatExactTime
import com.example.ui.components.formatRelativeTimestamp
import com.example.ui.theme.MonoLabelStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendDetailScreen(
    friendWithProximity: FriendWithProximity?,
    sightings: List<SightingEntity>,
    onBack: () -> Unit,
    onEdit: (Friend) -> Unit,
    onToggleFavorite: (Friend) -> Unit,
    onBlockFriend: (Friend) -> Unit,
    onUnblockFriend: (Friend) -> Unit,
    onDelete: (Friend) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(friendWithProximity?.friend?.name ?: "Friend Details") },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("detail_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Navigate back")
                }
            },
            actions = {
                if (friendWithProximity != null) {
                    IconButton(
                        onClick = { onToggleFavorite(friendWithProximity.friend) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("detail_favorite_button")
                    ) {
                        Icon(
                            imageVector = if (friendWithProximity.friend.isFavorite) {
                                Icons.Filled.Star
                            } else {
                                Icons.Outlined.StarBorder
                            },
                            contentDescription = "Toggle favorite",
                            tint = if (friendWithProximity.friend.isFavorite) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        )

        if (friendWithProximity == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Friend record not found.", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = onBack) {
                    Text("Go Back")
                }
            }
            return
        }

        val friend = friendWithProximity.friend
        val detection = friendWithProximity.activeDetection

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
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
                        FriendAvatar(
                            name = friend.displayLabel,
                            colorHex = friend.avatarColorHex,
                            isOnline = detection != null,
                            size = 72.dp
                        )
                        Text(
                            text = friend.displayLabel,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        if (friend.nickname.isNotBlank()) {
                            Text(
                                text = friend.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (friendWithProximity.isBlocked) {
                            Text(
                                text = "Blocked — hidden from Radar screen",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else if (detection != null) {
                            ProximityZoneBadge(
                                zone = detection.zone,
                                distanceMeters = detection.estimatedMeters
                            )
                            Text(
                                text = "Signal ${detection.rssi} dBm • Matched via ${
                                    if (friendWithProximity.matchedBy == "BEACON_ID") "Beacon ID" else "MAC Address"
                                }",
                                style = MonoLabelStyle,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = "Currently out of range • Last seen: ${formatRelativeTimestamp(friend.lastSeenTimestamp)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (friend.note.isNotBlank()) {
                            Text(
                                text = friend.note,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { onEdit(friend) },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("detail_edit_button")
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Edit")
                            }
                            OutlinedButton(
                                onClick = {
                                    if (friendWithProximity.isBlocked) {
                                        onUnblockFriend(friend)
                                    } else {
                                        onBlockFriend(friend)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("detail_block_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (friendWithProximity.isBlocked) {
                                        Icons.Filled.LockOpen
                                    } else {
                                        Icons.Filled.Block
                                    },
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (friendWithProximity.isBlocked) "Unblock" else "Block")
                            }
                        }

                        OutlinedButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("detail_delete_button")
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Remove Friend", color = MaterialTheme.colorScheme.error)
                        }
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
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Registered Device Identifiers",
                            style = MaterialTheme.typography.titleMedium
                        )
                        DetailRow(
                            label = "Beacon ID",
                            value = friend.beaconId.ifBlank { "Not set" }
                        )
                        HorizontalDivider()
                        DetailRow(
                            label = "Bluetooth MAC",
                            value = friend.macAddress.ifBlank { "Not set" }
                        )
                        HorizontalDivider()
                        DetailRow(
                            label = "Added On",
                            value = formatExactTime(friend.createdAt)
                        )
                    }
                }
            }

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
                        text = "Proximity Sightings (${sightings.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            if (sightings.isEmpty()) {
                item {
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = "No nearby sightings recorded yet for ${friend.name}. When they come within Bluetooth LE range, encounters are logged here locally.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(sightings, key = { it.id }) { sighting ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp),
                        shape = MaterialTheme.shapes.small,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = formatExactTime(sighting.timestamp),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Matched via ${
                                        if (sighting.matchedBy == "BEACON_ID") "Beacon ID" else "MAC"
                                    } • ${sighting.rssi} dBm",
                                    style = MonoLabelStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = ProximityMath.formatDistance(sighting.estimatedMeters),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Remove ${friend.name}?") },
                text = { Text("This deletes ${friend.name} and their sighting history from this device.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            onDelete(friend)
                            onBack()
                        },
                        modifier = Modifier.testTag("confirm_delete_friend_button")
                    ) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MonoLabelStyle,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
