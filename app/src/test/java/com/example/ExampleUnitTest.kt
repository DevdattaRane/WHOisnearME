package com.example

import com.example.data.ble.BleProximityManager
import com.example.data.local.entity.Friend
import com.example.data.model.DetectedPeer
import com.example.data.model.FriendWithProximity
import com.example.data.model.IdentifierValidator
import com.example.data.model.ProximityMath
import com.example.ui.components.buildRadarBlips
import com.example.ui.components.computeBaseAgeOpacity
import com.example.ui.components.computeSweepingMarkerOpacity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `beacon payload encodes and decodes cleanly within BLE limit`() {
        val payload = BleProximityManager.encodeBeaconPayload("A7F2-9C14", "Devdatta")
        assertTrue(payload.size <= 12)
        val decoded = BleProximityManager.decodeBeaconId(payload)
        assertEquals("A7F2-9C14", decoded)
    }

    @Test
    fun `validator rejects masked android MAC and invalid formats`() {
        val maskedResult = IdentifierValidator.validateFriendForm(
            name = "Alex",
            beaconId = "",
            macAddress = "02:00:00:00:00:00",
            existingFriends = emptyList(),
            editingFriendId = null
        )
        assertFalse(maskedResult.isValid)
        assertNotNull(maskedResult.macError)

        val validResult = IdentifierValidator.validateFriendForm(
            name = "Alex",
            beaconId = "a7f29c14",
            macAddress = "aa:bb:cc:dd:ee:ff",
            existingFriends = emptyList(),
            editingFriendId = null
        )
        assertTrue(validResult.isValid)
        assertEquals("A7F2-9C14", validResult.normalizedBeaconId)
        assertEquals("AA:BB:CC:DD:EE:FF", validResult.normalizedMac)
    }

    @Test
    fun `proximity distance estimation stays within bounds`() {
        val close = ProximityMath.estimateDistanceMeters(-45)
        val far = ProximityMath.estimateDistanceMeters(-95)
        assertTrue(close < far)
        assertTrue(close >= 0.3f)
        assertTrue(far <= 35.0f)
    }

    @Test
    fun `radar blips include live and recent room friends and sort live first`() {
        val now = 1_760_000_000_000L
        val recentRoomFriend = FriendWithProximity(
            friend = Friend(
                id = 1L,
                name = "Sam",
                nickname = "Sammy",
                deviceIdentifier = "1111-2222",
                lastSeenTimestamp = now - 60_000L,
                lastSeenDistanceMeters = 2.4f,
                lastSeenRssi = -63
            ),
            activeDetection = null,
            matchedBy = null
        )
        val liveFriend = FriendWithProximity(
            friend = Friend(
                id = 2L,
                name = "Maya",
                deviceIdentifier = "3333-4444"
            ),
            activeDetection = DetectedPeer(
                key = "BEACON:3333-4444",
                beaconId = "3333-4444",
                macAddress = "AA:BB:CC:DD:EE:FF",
                advertisedName = "Maya",
                rssi = -68,
                estimatedMeters = 5.0f,
                isWhoIsNearMeApp = true,
                lastUpdatedMs = now
            ),
            matchedBy = "BEACON_ID"
        )

        val liveOnly = buildRadarBlips(
            friends = listOf(recentRoomFriend, liveFriend),
            includeRecentRoomSightings = false,
            nowMs = now
        )
        assertEquals(1, liveOnly.size)
        assertEquals(2L, liveOnly.first().friendId)

        val withRoom = buildRadarBlips(
            friends = listOf(recentRoomFriend, liveFriend),
            includeRecentRoomSightings = true,
            nowMs = now
        )
        assertEquals(2, withRoom.size)
        assertTrue(withRoom.first().isLive)
        assertEquals("Sammy", withRoom[1].label)
    }

    @Test
    fun `marker opacity decays with timestamp age and brightens on radar sweep`() {
        val now = 1_760_000_000_000L
        val freshAlpha = computeBaseAgeOpacity(now - 5_000L, nowMs = now)
        val mediumAlpha = computeBaseAgeOpacity(now - 5 * 60_000L, nowMs = now)
        val oldAlpha = computeBaseAgeOpacity(now - 14 * 60_000L, nowMs = now)

        assertEquals(1.0f, freshAlpha, 0.01f)
        assertTrue(mediumAlpha < freshAlpha)
        assertTrue(oldAlpha < mediumAlpha)
        assertTrue(oldAlpha >= 0.28f)

        val (hitAlpha, hitPulse) = computeSweepingMarkerOpacity(
            lastSeenTimestamp = now - 8 * 60_000L,
            sweepAngleDeg = 95f,
            blipAngleDeg = 90f,
            isScanning = true,
            nowMs = now
        )
        val (idleAlpha, idlePulse) = computeSweepingMarkerOpacity(
            lastSeenTimestamp = now - 8 * 60_000L,
            sweepAngleDeg = 270f,
            blipAngleDeg = 90f,
            isScanning = true,
            nowMs = now
        )

        assertEquals(1.0f, hitPulse, 0.01f)
        assertEquals(0.0f, idlePulse, 0.01f)
        assertTrue(hitAlpha > idleAlpha)
    }

    @Test
    fun `blocked identifiers exclude friends from radar blips and match case-insensitively`() {
        val now = 1_760_000_000_000L
        val blockedPeer = DetectedPeer(
            key = "BEACON:9999-AAAA",
            beaconId = "9999-AAAA",
            macAddress = "11:22:33:44:55:66",
            advertisedName = "SpamBeacon",
            rssi = -52,
            estimatedMeters = 1.5f,
            isWhoIsNearMeApp = true,
            lastUpdatedMs = now
        )
        val blockedFriend = Friend(
            id = 9L,
            name = "BlockedUser",
            deviceIdentifier = "9999-aaaa",
            beaconId = "9999-AAAA",
            macAddress = "11:22:33:44:55:66",
            lastSeenTimestamp = now,
            lastSeenDistanceMeters = 1.5f,
            lastSeenRssi = -52
        )
        val allowedFriend = Friend(
            id = 10L,
            name = "AllowedUser",
            deviceIdentifier = "7777-BBBB",
            beaconId = "7777-BBBB",
            lastSeenTimestamp = now,
            lastSeenDistanceMeters = 3.0f,
            lastSeenRssi = -64
        )

        val blockedSet = setOf("9999-AAAA")
        assertTrue(com.example.data.repository.FriendRepository.isPeerBlocked(blockedPeer, blockedSet))
        assertTrue(com.example.data.repository.FriendRepository.isFriendBlocked(blockedFriend, blockedSet))
        assertFalse(com.example.data.repository.FriendRepository.isFriendBlocked(allowedFriend, blockedSet))

        val blips = buildRadarBlips(
            friends = listOf(
                FriendWithProximity(
                    friend = blockedFriend,
                    activeDetection = blockedPeer,
                    matchedBy = "BEACON_ID",
                    isBlocked = true
                ),
                FriendWithProximity(
                    friend = allowedFriend,
                    activeDetection = null,
                    matchedBy = null,
                    isBlocked = false
                )
            ),
            includeRecentRoomSightings = true,
            nowMs = now
        )

        assertEquals(1, blips.size)
        assertEquals(10L, blips.first().friendId)
    }

    @Test
    fun `rssiToDistanceMeters translates RSSI into physical meters and formats radar labels`() {
        val oneMeter = ProximityMath.rssiToDistanceMeters(rssi = -59)
        assertEquals(1.0f, oneMeter, 0.02f)

        val tenMeters = ProximityMath.rssiToDistanceMeters(rssi = -81)
        assertEquals(10.0f, tenMeters, 0.1f)

        val veryClose = ProximityMath.rssiToDistanceMeters(rssi = -30)
        assertEquals(0.3f, veryClose, 0.01f)

        assertEquals("0.8 m", ProximityMath.formatDistance(0.8f))
        assertEquals("~2.4 m", ProximityMath.formatDistance(2.4f))
        assertEquals("~10 m", ProximityMath.formatDistance(10.2f))
        assertEquals("4m", ProximityMath.formatRadarRingMeters(3.96f))
        assertEquals("2.5m", ProximityMath.formatRadarRingMeters(2.5f))
    }

    @Test
    fun `proximity alert transition triggers on entry and enforces hysteresis and cooldown`() {
        val t0 = 1_760_000_000_000L
        val threshold = 10f

        // Outside threshold initially: no alert
        val initialFar = com.example.data.notifications.ProximityNotificationService.evaluateAlertTransition(
            previousState = null,
            distanceMeters = 14.0f,
            thresholdMeters = threshold,
            nowMs = t0
        )
        assertFalse(initialFar.shouldTrigger)
        assertFalse(initialFar.nextState.isInsideThreshold)

        // New friend enters threshold: triggers alert
        val enterRange = com.example.data.notifications.ProximityNotificationService.evaluateAlertTransition(
            previousState = initialFar.nextState,
            distanceMeters = 6.5f,
            thresholdMeters = threshold,
            nowMs = t0 + 5_000L
        )
        assertTrue(enterRange.shouldTrigger)
        assertTrue(enterRange.nextState.isInsideThreshold)

        // Boundary jitter slightly outside threshold (<= threshold + 2.0f hysteresis): stays armed without re-alerting
        val jitter = com.example.data.notifications.ProximityNotificationService.evaluateAlertTransition(
            previousState = enterRange.nextState,
            distanceMeters = 11.0f,
            thresholdMeters = threshold,
            nowMs = t0 + 10_000L
        )
        assertFalse(jitter.shouldTrigger)
        assertTrue(jitter.nextState.isInsideThreshold)

        // Exits well beyond hysteresis, then re-enters BEFORE cooldown: suppressed
        val exited = com.example.data.notifications.ProximityNotificationService.evaluateAlertTransition(
            previousState = jitter.nextState,
            distanceMeters = 15.0f,
            thresholdMeters = threshold,
            nowMs = t0 + 15_000L
        )
        assertFalse(exited.nextState.isInsideThreshold)

        val quickReEntry = com.example.data.notifications.ProximityNotificationService.evaluateAlertTransition(
            previousState = exited.nextState,
            distanceMeters = 5.0f,
            thresholdMeters = threshold,
            nowMs = t0 + 30_000L
        )
        assertFalse(quickReEntry.shouldTrigger)

        // Exits again and re-enters AFTER cooldown: triggers alert again
        val exitedLater = com.example.data.notifications.ProximityNotificationService.evaluateAlertTransition(
            previousState = quickReEntry.nextState,
            distanceMeters = 16.0f,
            thresholdMeters = threshold,
            nowMs = t0 + 140_000L
        )
        val reEntryAfterCooldown = com.example.data.notifications.ProximityNotificationService.evaluateAlertTransition(
            previousState = exitedLater.nextState,
            distanceMeters = 4.0f,
            thresholdMeters = threshold,
            nowMs = t0 + 150_000L
        )
        assertTrue(reEntryAfterCooldown.shouldTrigger)
    }
}
