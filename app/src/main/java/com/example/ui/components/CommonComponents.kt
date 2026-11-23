package com.example.ui.components

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.BleRadioState
import com.example.data.model.FriendWithProximity
import com.example.data.model.ProximityMath
import com.example.data.model.ProximityZone
import com.example.ui.theme.MonoLabelStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatRelativeTimestamp(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0L) return "Never seen nearby"
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return if (diff < 60_000L) {
        "Just now"
    } else {
        DateUtils.getRelativeTimeSpanString(
            timestamp,
            now,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    }
}

fun formatExactTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
fun FriendAvatar(
    name: String,
    colorHex: Long,
    isOnline: Boolean,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val initials = name.trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifEmpty { "?" }

    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(colorHex)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        if (isOnline) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
fun ProximityZoneBadge(
    zone: ProximityZone,
    distanceMeters: Float,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = when (zone) {
        ProximityZone.IMMEDIATE -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        ProximityZone.NEAR -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        ProximityZone.FAR -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = bg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Sensors,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = fg
            )
            Text(
                text = "${zone.label} • ${ProximityMath.formatDistance(distanceMeters)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = fg
            )
        }
    }
}

@Composable
fun RadioStatusBanner(
    radioState: BleRadioState,
    bleError: String?,
    onActionClick: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        radioState == BleRadioState.PERMISSION_REQUIRED -> {
            StatusBanner(
                icon = Icons.Filled.Radar,
                title = "Nearby permission needed",
                subtitle = "Allow Nearby Devices access so WHOisnearME can scan and broadcast offline BLE beacons without GPS location.",
                actionLabel = "Grant Access",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                buttonTag = "grant_permission_button",
                onAction = onActionClick,
                modifier = modifier
            )
        }
        radioState == BleRadioState.BLUETOOTH_OFF -> {
            StatusBanner(
                icon = Icons.Filled.BluetoothDisabled,
                title = "Bluetooth is turned off",
                subtitle = "Enable Bluetooth on your phone to discover nearby friends and broadcast your Beacon ID.",
                actionLabel = "Turn On Bluetooth",
                containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                buttonTag = "enable_bluetooth_button",
                onAction = onActionClick,
                modifier = modifier
            )
        }
        radioState == BleRadioState.BLE_UNAVAILABLE -> {
            StatusBanner(
                icon = Icons.Filled.BluetoothDisabled,
                title = "BLE radio unavailable",
                subtitle = "This device or emulator has no Bluetooth Low Energy hardware. You can still manage friends, view history, and share your Beacon ID.",
                actionLabel = null,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                buttonTag = "ble_unavailable_banner",
                onAction = {},
                modifier = modifier
            )
        }
        bleError != null -> {
            StatusBanner(
                icon = Icons.Filled.ErrorOutline,
                title = "Scanner notice",
                subtitle = bleError,
                actionLabel = "Dismiss",
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                buttonTag = "dismiss_ble_error_button",
                onAction = onDismissError,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun StatusBanner(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String?,
    containerColor: Color,
    contentColor: Color,
    buttonTag: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.88f)
            )
            if (actionLabel != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onAction,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag(buttonTag),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(text = actionLabel)
                }
            }
        }
    }
}

@Composable
fun FriendProximityCard(
    item: FriendWithProximity,
    onClick: () -> Unit,
    onToggleFavorite: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val friend = item.friend
    val detection = item.activeDetection
    val isNearby = detection != null

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("friend_card_${friend.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isNearby) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
            }
        ),
        border = BorderStroke(
            width = if (isNearby) 1.5.dp else 1.dp,
            color = if (isNearby) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isNearby) 3.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FriendAvatar(
                name = friend.displayLabel,
                colorHex = friend.avatarColorHex,
                isOnline = isNearby,
                size = 50.dp
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (friend.nickname.isNotBlank()) {
                            "${friend.nickname} (${friend.name})"
                        } else {
                            friend.name
                        },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                if (item.isBlocked) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = "Blocked • Hidden from radar",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                } else if (detection != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProximityZoneBadge(
                            zone = detection.zone,
                            distanceMeters = detection.estimatedMeters
                        )
                        Text(
                            text = "${detection.rssi} dBm",
                            style = MonoLabelStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = "Last seen: ${formatRelativeTimestamp(friend.lastSeenTimestamp)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                val identifierText = buildString {
                    if (friend.beaconId.isNotBlank()) append("ID ${friend.beaconId}")
                    if (friend.beaconId.isNotBlank() && friend.macAddress.isNotBlank()) append("  •  ")
                    if (friend.macAddress.isNotBlank()) append(friend.macAddress)
                }
                Text(
                    text = identifierText,
                    style = MonoLabelStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onToggleFavorite != null) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("favorite_toggle_${friend.id}")
                ) {
                    Icon(
                        imageVector = if (friend.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (friend.isFavorite) "Remove favorite" else "Mark favorite",
                        tint = if (friend.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
