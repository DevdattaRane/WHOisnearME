package com.example.data.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.DetectedPeer
import com.example.data.model.FriendWithProximity
import com.example.data.model.ProximityMath
import com.example.data.preferences.UserPreferences
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Coordinates threshold-based proximity alerts using subtle haptic feedback and local
 * Android notifications whenever a new or saved friend is detected within range.
 */
class ProximityNotificationService(private val context: Context) {

    data class AlertTrackState(
        val lastAlertTimestampMs: Long,
        val isInsideThreshold: Boolean
    )

    private val trackStates = ConcurrentHashMap<String, AlertTrackState>()

    init {
        ensureNotificationChannel()
    }

    fun evaluateFriendDetections(
        friendsWithProximity: List<FriendWithProximity>,
        unmatchedPeers: List<DetectedPeer>,
        prefs: UserPreferences,
        nowMs: Long = System.currentTimeMillis()
    ) {
        if (!prefs.proximityNotificationsEnabled && !prefs.hapticFeedbackEnabled) return

        val threshold = prefs.alertRangeMeters

        friendsWithProximity.forEach { item ->
            if (item.isBlocked) return@forEach
            val detection = item.activeDetection ?: return@forEach
            val canonicalKey = canonicalKey(
                beaconId = item.friend.beaconId.ifBlank { detection.beaconId },
                macAddress = item.friend.macAddress.ifBlank { detection.macAddress },
                fallback = "FRIEND_${item.friend.id}"
            )
            val isNewArrival = item.friend.lastSeenTimestamp == null
            evaluateCandidate(
                canonicalKey = canonicalKey,
                displayName = item.friend.displayLabel,
                distanceMeters = detection.estimatedMeters,
                thresholdMeters = threshold,
                isNewlyDiscovered = isNewArrival,
                prefs = prefs,
                nowMs = nowMs
            )
        }

        unmatchedPeers.filter { it.isWhoIsNearMeApp }.forEach { peer ->
            val canonicalKey = canonicalKey(
                beaconId = peer.beaconId,
                macAddress = peer.macAddress,
                fallback = peer.key
            )
            val displayName = peer.advertisedName.ifBlank { "WHOisnearME Friend" }
            evaluateCandidate(
                canonicalKey = canonicalKey,
                displayName = displayName,
                distanceMeters = peer.estimatedMeters,
                thresholdMeters = threshold,
                isNewlyDiscovered = true,
                prefs = prefs,
                nowMs = nowMs
            )
        }
    }

    fun notifyFriendDetectedInBackground(
        canonicalIdentifier: String,
        displayName: String,
        distanceMeters: Float,
        isNewlyDiscovered: Boolean,
        prefs: UserPreferences,
        nowMs: Long = System.currentTimeMillis()
    ) {
        if (!prefs.proximityNotificationsEnabled && !prefs.hapticFeedbackEnabled) return
        val key = canonicalIdentifier.trim().uppercase(Locale.US)
        if (key.isEmpty()) return

        evaluateCandidate(
            canonicalKey = key,
            displayName = displayName,
            distanceMeters = distanceMeters,
            thresholdMeters = prefs.alertRangeMeters,
            isNewlyDiscovered = isNewlyDiscovered,
            prefs = prefs,
            nowMs = nowMs
        )
    }

    private fun evaluateCandidate(
        canonicalKey: String,
        displayName: String,
        distanceMeters: Float,
        thresholdMeters: Float,
        isNewlyDiscovered: Boolean,
        prefs: UserPreferences,
        nowMs: Long
    ) {
        val currentState = trackStates[canonicalKey]
        val decision = evaluateAlertTransition(
            previousState = currentState,
            distanceMeters = distanceMeters,
            thresholdMeters = thresholdMeters,
            nowMs = nowMs
        )
        trackStates[canonicalKey] = decision.nextState

        if (decision.shouldTrigger) {
            dispatchFeedback(
                canonicalKey = canonicalKey,
                displayName = displayName,
                distanceMeters = distanceMeters,
                thresholdMeters = thresholdMeters,
                isNewlyDiscovered = isNewlyDiscovered,
                enableNotification = prefs.proximityNotificationsEnabled,
                enableHaptics = prefs.hapticFeedbackEnabled
            )
        }
    }

    fun sendTestAlert(thresholdMeters: Float, enableNotification: Boolean, enableHaptics: Boolean): Boolean {
        return dispatchFeedback(
            canonicalKey = "TEST_ALERT",
            displayName = "Alex (Sample Friend)",
            distanceMeters = (thresholdMeters * 0.4f).coerceAtLeast(1.2f),
            thresholdMeters = thresholdMeters,
            isNewlyDiscovered = true,
            enableNotification = enableNotification,
            enableHaptics = enableHaptics
        )
    }

    fun clearStateForIdentifier(canonicalIdentifier: String) {
        trackStates.remove(canonicalIdentifier.trim().uppercase(Locale.US))
    }

    fun clearAllStates() {
        trackStates.clear()
    }

    private fun dispatchFeedback(
        canonicalKey: String,
        displayName: String,
        distanceMeters: Float,
        thresholdMeters: Float,
        isNewlyDiscovered: Boolean,
        enableNotification: Boolean,
        enableHaptics: Boolean
    ): Boolean {
        var triggeredAnything = false
        if (enableHaptics) {
            triggerSubtleHapticPulse()
            triggeredAnything = true
        }
        if (enableNotification && postLocalProximityNotification(
                canonicalKey = canonicalKey,
                displayName = displayName,
                distanceMeters = distanceMeters,
                thresholdMeters = thresholdMeters,
                isNewlyDiscovered = isNewlyDiscovered
            )
        ) {
            triggeredAnything = true
        }
        return triggeredAnything
    }

    @SuppressLint("MissingPermission")
    fun triggerSubtleHapticPulse() {
        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator == null || !vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0L, 35L, 55L, 45L),
                        intArrayOf(0, 95, 0, 125),
                        -1
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0L, 35L, 55L, 45L), -1)
            }
        } catch (_: Exception) {
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun postLocalProximityNotification(
        canonicalKey: String,
        displayName: String,
        distanceMeters: Float,
        thresholdMeters: Float,
        isNewlyDiscovered: Boolean
    ): Boolean {
        if (!hasNotificationPermission()) return false
        ensureNotificationChannel()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false

        val openAppIntent = PendingIntent.getActivity(
            context,
            abs(canonicalKey.hashCode()),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedDist = ProximityMath.formatDistance(distanceMeters)
        val title = if (isNewlyDiscovered) {
            "New friend nearby: $displayName"
        } else {
            "Friend nearby: $displayName"
        }
        val body = "Detected $formattedDist away (within ${thresholdMeters.roundToInt()}m alert range)"

        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)
            .build()

        val notificationId = ALERT_NOTIFICATION_BASE_ID + (abs(canonicalKey.hashCode()) % 5000)
        nm.notify(notificationId, notification)
        return true
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val existing = nm.getNotificationChannel(ALERT_CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "Nearby Friend Proximity Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifies when a new or saved friend is detected within your distance threshold"
                    enableVibration(false) // Subtle haptics handled separately via Vibrator
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    data class TransitionDecision(
        val shouldTrigger: Boolean,
        val nextState: AlertTrackState
    )

    companion object {
        const val ALERT_CHANNEL_ID = "whoisnearme_proximity_alerts"
        private const val ALERT_NOTIFICATION_BASE_ID = 20_000
        const val ALERT_COOLDOWN_MS = 120_000L
        const val EXIT_HYSTERESIS_METERS = 2.0f

        fun canonicalKey(beaconId: String, macAddress: String, fallback: String): String {
            return beaconId.trim().ifBlank { macAddress.trim() }.ifBlank { fallback.trim() }
                .uppercase(Locale.US)
        }

        /**
         * Pure state-machine evaluation for threshold crossing and alert cooldowns:
         * - Triggers when a peer enters `distanceMeters <= thresholdMeters` and was not already
         *   inside the threshold (or cooldown elapsed after re-entering).
         * - Applies hysteresis (`thresholdMeters + EXIT_HYSTERESIS_METERS`) before resetting
         *   `isInsideThreshold` to false, avoiding boundary jitter spam.
         */
        fun evaluateAlertTransition(
            previousState: AlertTrackState?,
            distanceMeters: Float,
            thresholdMeters: Float,
            nowMs: Long
        ): TransitionDecision {
            val isWithinThreshold = distanceMeters <= thresholdMeters
            val exitedBeyondHysteresis = distanceMeters > (thresholdMeters + EXIT_HYSTERESIS_METERS)

            if (!isWithinThreshold) {
                val stillInside = if (exitedBeyondHysteresis) {
                    false
                } else {
                    previousState?.isInsideThreshold ?: false
                }
                return TransitionDecision(
                    shouldTrigger = false,
                    nextState = AlertTrackState(
                        lastAlertTimestampMs = previousState?.lastAlertTimestampMs ?: 0L,
                        isInsideThreshold = stillInside
                    )
                )
            }

            if (previousState == null) {
                return TransitionDecision(
                    shouldTrigger = true,
                    nextState = AlertTrackState(
                        lastAlertTimestampMs = nowMs,
                        isInsideThreshold = true
                    )
                )
            }

            val cooldownElapsed = (nowMs - previousState.lastAlertTimestampMs) >= ALERT_COOLDOWN_MS
            val shouldTrigger = !previousState.isInsideThreshold && cooldownElapsed

            return TransitionDecision(
                shouldTrigger = shouldTrigger,
                nextState = AlertTrackState(
                    lastAlertTimestampMs = if (shouldTrigger) nowMs else previousState.lastAlertTimestampMs,
                    isInsideThreshold = true
                )
            )
        }
    }
}
