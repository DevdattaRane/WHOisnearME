package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FriendWithProximity
import com.example.data.model.ProximityMath
import com.example.ui.theme.MonoLabelStyle
import com.example.ui.theme.RadarAmberBright
import com.example.ui.theme.RadarMidnight
import com.example.ui.theme.RadarNavy
import com.example.ui.theme.RadarSky
import com.example.ui.theme.RadarTealBright
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class RadarBlip(
    val friendId: Long,
    val label: String,
    val distanceMeters: Float,
    val avatarColorHex: Long,
    val isLive: Boolean,
    val rssi: Int?,
    val lastSeenTimestamp: Long?
)

private data class PlottedRadarBlip(
    val blip: RadarBlip,
    val angleDegrees: Float,
    val dx: Float,
    val dy: Float
)

/**
 * Computes base marker opacity from the age of the friend's last proximity timestamp.
 * Fresh sightings (<= 15s) return 1.0f; older sightings decay smoothly down to 0.28f over 15 minutes.
 */
fun computeBaseAgeOpacity(
    lastSeenTimestamp: Long?,
    nowMs: Long = System.currentTimeMillis(),
    maxAgeWindowMs: Long = 15 * 60 * 1000L
): Float {
    if (lastSeenTimestamp == null) return 0.28f
    val ageMs = (nowMs - lastSeenTimestamp).coerceAtLeast(0L)
    if (ageMs <= 15_000L) return 1.0f
    if (ageMs >= maxAgeWindowMs) return 0.28f

    val decayFraction = (ageMs - 15_000L).toFloat() / (maxAgeWindowMs - 15_000L).toFloat()
    return (0.95f - 0.67f * decayFraction).coerceIn(0.28f, 1.0f)
}

/**
 * Combines timestamp-age opacity with the live radar sweep angle so markers brighten
 * briefly as the radar beam sweeps across their radial angle and then settle back to their age opacity.
 */
fun computeSweepingMarkerOpacity(
    lastSeenTimestamp: Long?,
    sweepAngleDeg: Float,
    blipAngleDeg: Float,
    isScanning: Boolean,
    nowMs: Long = System.currentTimeMillis()
): Pair<Float, Float> {
    val baseAlpha = computeBaseAgeOpacity(lastSeenTimestamp, nowMs)
    if (!isScanning) return baseAlpha to 0f

    val angularLag = ((sweepAngleDeg - blipAngleDeg) % 360f + 360f) % 360f
    val sweepHighlight = when {
        angularLag <= 15f -> 1f
        angularLag <= 115f -> 1f - ((angularLag - 15f) / 100f)
        else -> 0f
    }.coerceIn(0f, 1f)

    val effectiveAlpha = (baseAlpha + sweepHighlight * 0.34f * (1.15f - baseAlpha * 0.5f))
        .coerceIn(0.28f, 1.0f)
    return effectiveAlpha to sweepHighlight
}

fun buildRadarBlips(
    friends: List<FriendWithProximity>,
    includeRecentRoomSightings: Boolean,
    recentWindowMs: Long = 15 * 60 * 1000L,
    nowMs: Long = System.currentTimeMillis()
): List<RadarBlip> {
    return friends.mapNotNull { item ->
        if (item.isBlocked) return@mapNotNull null
        val detection = item.activeDetection
        val friend = item.friend
        if (detection != null) {
            RadarBlip(
                friendId = friend.id,
                label = friend.displayLabel,
                distanceMeters = detection.estimatedMeters,
                avatarColorHex = friend.avatarColorHex,
                isLive = true,
                rssi = detection.rssi,
                lastSeenTimestamp = detection.lastUpdatedMs
            )
        } else if (
            includeRecentRoomSightings &&
            friend.lastSeenTimestamp != null &&
            friend.lastSeenDistanceMeters != null &&
            (nowMs - friend.lastSeenTimestamp) <= recentWindowMs
        ) {
            RadarBlip(
                friendId = friend.id,
                label = friend.displayLabel,
                distanceMeters = friend.lastSeenDistanceMeters,
                avatarColorHex = friend.avatarColorHex,
                isLive = false,
                rssi = friend.lastSeenRssi,
                lastSeenTimestamp = friend.lastSeenTimestamp
            )
        } else {
            null
        }
    }.sortedWith(
        compareByDescending<RadarBlip> { it.isLive }
            .thenBy { it.distanceMeters }
    )
}

@Composable
fun ProximityRadarView(
    blips: List<RadarBlip>,
    isScanning: Boolean,
    isAdvertising: Boolean,
    alertRangeMeters: Float,
    centerDeviceLabel: String = "YOU",
    selectedFriendId: Long? = null,
    onFriendClick: (Long) -> Unit,
    onExpandClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar_sweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep_angle"
    )
    val pulseRadiusFactor by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.96f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_ring"
    )

    // Periodically refresh timestamp so aging markers fade smoothly while on screen
    var currentNowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000L)
            currentNowMs = System.currentTimeMillis()
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = RadarNavy,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF132D4E), RadarMidnight)
                    )
                )
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isScanning) "ACTIVE RADAR" else "RADAR STANDBY",
                    style = MonoLabelStyle,
                    color = if (isScanning) RadarTealBright else RadarSky.copy(alpha = 0.65f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isAdvertising) "BEACON ON" else "HIDDEN",
                        style = MonoLabelStyle,
                        color = if (isAdvertising) RadarTealBright else Color(0xFF94A3B8)
                    )
                    if (onExpandClick != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = onExpandClick,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("expand_radar_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.OpenInFull,
                                contentDescription = "Open full-screen radar visualizer",
                                tint = RadarSky
                            )
                        }
                    }
                }
            }

            val radarMaxMeters = alertRangeMeters.coerceAtLeast(3f)

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .aspectRatio(1f)
            ) {
                val density = LocalDensity.current
                val widthPx = with(density) { maxWidth.toPx() }
                val heightPx = with(density) { maxHeight.toPx() }
                val maxRadiusPx = min(widthPx, heightPx) * 0.44f
                val centerPx = Offset(widthPx / 2f, heightPx / 2f)

                val plottedBlips = blips.take(12).mapIndexed { index, blip ->
                    val normalizedDist = (blip.distanceMeters / radarMaxMeters).coerceIn(0.18f, 0.92f)
                    val angleDegrees = (((blip.friendId * 137.5 + index * 67.0) % 360.0).toFloat() + 360f) % 360f
                    val angleRad = angleDegrees * PI / 180.0
                    val dx = (cos(angleRad) * maxRadiusPx * normalizedDist).toFloat()
                    val dy = (sin(angleRad) * maxRadiusPx * normalizedDist).toFloat()
                    PlottedRadarBlip(
                        blip = blip,
                        angleDegrees = angleDegrees,
                        dx = dx,
                        dy = dy
                    )
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = min(size.width, size.height) * 0.44f
                    val center = Offset(size.width / 2f, size.height / 2f)

                    // Concentric distance rings
                    val ringRadii = listOf(0.33f, 0.66f, 1.0f)
                    ringRadii.forEachIndexed { idx, factor ->
                        drawCircle(
                            color = RadarTealBright.copy(alpha = if (idx == 2) 0.35f else 0.2f),
                            radius = radius * factor,
                            center = center,
                            style = Stroke(width = if (idx == 2) 2.dp.toPx() else 1.dp.toPx())
                        )
                    }

                    // Crosshair axes
                    drawLine(
                        color = RadarSky.copy(alpha = 0.14f),
                        start = Offset(center.x - radius, center.y),
                        end = Offset(center.x + radius, center.y),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = RadarSky.copy(alpha = 0.14f),
                        start = Offset(center.x, center.y - radius),
                        end = Offset(center.x, center.y + radius),
                        strokeWidth = 1.dp.toPx()
                    )

                    if (isScanning) {
                        drawCircle(
                            color = RadarTealBright.copy(alpha = (1f - pulseRadiusFactor) * 0.42f),
                            radius = radius * pulseRadiusFactor,
                            center = center,
                            style = Stroke(width = 2.dp.toPx())
                        )

                        // Sweeping radar beam cone (leading edge at sweepAngle)
                        drawArc(
                            brush = Brush.sweepGradient(
                                0.0f to Color.Transparent,
                                0.72f to Color.Transparent,
                                0.92f to RadarTealBright.copy(alpha = 0.18f),
                                1.0f to RadarTealBright.copy(alpha = 0.48f),
                                center = center
                            ),
                            startAngle = sweepAngle - 75f,
                            sweepAngle = 75f,
                            useCenter = true,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2f, radius * 2f)
                        )

                        // Sharp leading-edge sweep beam line
                        val beamRad = sweepAngle * PI / 180.0
                        drawLine(
                            color = RadarTealBright.copy(alpha = 0.75f),
                            start = center,
                            end = Offset(
                                center.x + (cos(beamRad) * radius).toFloat(),
                                center.y + (sin(beamRad) * radius).toFloat()
                            ),
                            strokeWidth = 1.8.dp.toPx()
                        )
                    }

                    // Radial vector lines & sweep-ping halos modulated by timestamp age + sweep hit
                    plottedBlips.forEach { item ->
                        val (markerOpacity, sweepHighlight) = computeSweepingMarkerOpacity(
                            lastSeenTimestamp = item.blip.lastSeenTimestamp,
                            sweepAngleDeg = sweepAngle,
                            blipAngleDeg = item.angleDegrees,
                            isScanning = isScanning,
                            nowMs = currentNowMs
                        )
                        val target = Offset(center.x + item.dx, center.y + item.dy)
                        val lineColor = if (item.blip.isLive) RadarTealBright else RadarAmberBright
                        val baseLineAlpha = if (item.blip.friendId == selectedFriendId) 0.85f else 0.42f

                        drawLine(
                            color = lineColor.copy(alpha = (baseLineAlpha * markerOpacity).coerceIn(0.12f, 0.9f)),
                            start = center,
                            end = target,
                            strokeWidth = if (item.blip.friendId == selectedFriendId) 2.dp.toPx() else 1.2.dp.toPx(),
                            pathEffect = if (item.blip.isLive) null else PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                        )

                        if (sweepHighlight > 0.05f) {
                            val pingRadius = (18f + (1f - sweepHighlight) * 14f).dp.toPx()
                            drawCircle(
                                color = lineColor.copy(alpha = sweepHighlight * markerOpacity * 0.55f),
                                radius = pingRadius,
                                center = target,
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }

                    // Center current device node
                    drawCircle(
                        color = RadarTealBright.copy(alpha = 0.24f),
                        radius = 20.dp.toPx(),
                        center = center
                    )
                    drawCircle(
                        color = RadarTealBright,
                        radius = 8.dp.toPx(),
                        center = center
                    )
                }

                // Concentric ring physical distance labels synced to radarMaxMeters
                Text(
                    text = ProximityMath.formatRadarRingMeters(radarMaxMeters * 0.33f),
                    style = MonoLabelStyle.copy(fontSize = 10.sp),
                    color = RadarSky.copy(alpha = 0.78f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset { IntOffset(0, -(maxRadiusPx * 0.33f).roundToInt() - 10) }
                        .testTag("radar_ring_label_inner")
                )
                Text(
                    text = ProximityMath.formatRadarRingMeters(radarMaxMeters * 0.66f),
                    style = MonoLabelStyle.copy(fontSize = 10.sp),
                    color = RadarSky.copy(alpha = 0.78f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset { IntOffset(0, -(maxRadiusPx * 0.66f).roundToInt() - 10) }
                        .testTag("radar_ring_label_mid")
                )
                Text(
                    text = ProximityMath.formatRadarRingMeters(radarMaxMeters),
                    style = MonoLabelStyle.copy(fontSize = 10.sp),
                    color = RadarSky.copy(alpha = 0.78f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset { IntOffset(0, -maxRadiusPx.roundToInt() - 10) }
                        .testTag("radar_ring_label_outer")
                )

                // Center device pill
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = RadarMidnight.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = 22.dp)
                ) {
                    Text(
                        text = centerDeviceLabel.take(10),
                        style = MonoLabelStyle.copy(fontSize = 9.sp),
                        color = RadarTealBright,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Interactive friend blips with RSSI-translated physical distance labels & sweep opacity
                val nodeHalfPx = with(density) { 24.dp.toPx() }
                plottedBlips.forEach { item ->
                    val blip = item.blip
                    val (markerOpacity, _) = computeSweepingMarkerOpacity(
                        lastSeenTimestamp = blip.lastSeenTimestamp,
                        sweepAngleDeg = sweepAngle,
                        blipAngleDeg = item.angleDegrees,
                        isScanning = isScanning,
                        nowMs = currentNowMs
                    )

                    val initials = blip.label.trim()
                        .split(" ")
                        .filter { it.isNotBlank() }
                        .take(2)
                        .joinToString("") { it.first().uppercaseChar().toString() }
                        .ifEmpty { "?" }
                    val ringColor = if (blip.isLive) RadarTealBright else RadarAmberBright
                    val isSelected = blip.friendId == selectedFriendId
                    val distanceText = ProximityMath.formatDistance(blip.distanceMeters)
                    val labelText = if (isSelected && blip.rssi != null) {
                        "${blip.label.take(8)} • $distanceText (${blip.rssi} dBm)"
                    } else {
                        "${blip.label.take(8)} • $distanceText"
                    }

                    Column(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (centerPx.x + item.dx - nodeHalfPx).roundToInt(),
                                    (centerPx.y + item.dy - nodeHalfPx).roundToInt()
                                )
                            }
                            .width(48.dp)
                            .graphicsLayer { alpha = markerOpacity },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .clickable { onFriendClick(blip.friendId) }
                                .testTag("radar_blip_${blip.friendId}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(if (isSelected) 34.dp else 30.dp)
                                    .clip(CircleShape)
                                    .background(Color(blip.avatarColorHex))
                                    .border(
                                        width = if (isSelected) 3.dp else 2.dp,
                                        color = if (isSelected) Color.White else ringColor,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = initials,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = RadarMidnight.copy(alpha = 0.92f),
                            border = BorderStroke(
                                width = 1.dp,
                                color = ringColor.copy(alpha = if (isSelected) 0.9f else 0.45f)
                            ),
                            modifier = Modifier
                                .offset(y = (-4).dp)
                                .wrapContentWidth(unbounded = true)
                                .clickable { onFriendClick(blip.friendId) }
                                .testTag("radar_blip_distance_${blip.friendId}")
                        ) {
                            Text(
                                text = labelText,
                                style = MonoLabelStyle.copy(
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = ringColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val liveCount = blips.count { it.isLive }
                val recentCount = blips.count { !it.isLive }
                Text(
                    text = when {
                        blips.isEmpty() -> "Listening for friends within ~${radarMaxMeters.roundToInt()}m"
                        recentCount > 0 -> "$liveCount live • $recentCount recent (fades with age)"
                        else -> "$liveCount friend${if (liveCount == 1) "" else "s"} nearby • Tap node"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCBD5E1),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "RSSI → m",
                    style = MonoLabelStyle.copy(fontSize = 10.sp),
                    color = RadarSky.copy(alpha = 0.72f)
                )
            }
        }
    }
}
