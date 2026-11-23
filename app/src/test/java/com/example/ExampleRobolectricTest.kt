package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ble.BleProximityManager
import com.example.data.local.AppDatabase
import com.example.data.local.entity.Friend
import com.example.data.local.entity.SightingEntity
import com.example.data.model.IdentifierValidator
import com.example.data.model.ProximityMath
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `app name matches WHOisnearME`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("WHOisnearME", appName)
    }

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
    fun `room persists friends and sightings across queries`() = runTest {
        val friendId = db.friendDao().insertFriend(
            Friend(
                name = "Samuel Lee",
                nickname = "Sam",
                deviceIdentifier = "12AB-34CD",
                beaconId = "12AB-34CD",
                macAddress = "11:22:33:44:55:66"
            )
        )
        val found = db.friendDao().findFriendByDeviceIdentifier("12ab-34cd")
        assertNotNull(found)
        assertEquals("Sam", found?.nickname)

        val seenAt = 1_760_000_000_000L
        db.friendDao().updateProximityTimestamp(
            id = friendId,
            timestamp = seenAt,
            distanceMeters = 1.9f,
            rssi = -58
        )
        val updated = db.friendDao().getFriendById(friendId)
        assertEquals(seenAt, updated?.lastSeenTimestamp)
        assertEquals(-58, updated?.lastSeenRssi)

        db.sightingDao().insertSighting(
            SightingEntity(
                friendId = friendId,
                friendName = "Sam",
                matchedBy = "BEACON_ID",
                rssi = -62,
                estimatedMeters = ProximityMath.estimateDistanceMeters(-62)
            )
        )

        val friends = db.friendDao().getAllFriends().first()
        val sightings = db.sightingDao().getRecentSightings().first()
        assertEquals(1, friends.size)
        assertEquals("Sam", friends.first().nickname)
        assertEquals(1, sightings.size)

        db.friendDao().deleteFriendById(friendId)
        val remainingSightings = db.sightingDao().getRecentSightings().first()
        assertTrue(remainingSightings.isEmpty())
    }

    @Test
    fun `main navigation tabs and add friend flow work without crashing`() {
        composeRule.onNodeWithText("WHOisnearME").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_tab_friends").performClick()
        composeRule.onNodeWithText("Saved Friends").assertIsDisplayed()

        composeRule.onNodeWithTag("friends_fab_add").performClick()
        composeRule.onNodeWithTag("friend_name_input").performTextInput("Maya")
        composeRule.onNodeWithTag("friend_beacon_input").performTextInput("A7F29C14")
        composeRule.onNodeWithTag("friend_form_save_button").performClick()

        composeRule.onNodeWithText("Maya").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_tab_my_device").performClick()
        composeRule.onNodeWithText("My Device Beacon").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_tab_history").performClick()
        composeRule.onNodeWithText("Sightings & Settings").assertIsDisplayed()
    }
}
