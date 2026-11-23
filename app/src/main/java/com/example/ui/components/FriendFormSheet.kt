package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.data.model.DetectedPeer
import com.example.ui.theme.FriendAvatarColors
import com.example.ui.theme.MonoLabelStyle
import com.example.ui.viewmodel.FriendFormState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendFormSheet(
    formState: FriendFormState,
    discoveredPeers: List<DetectedPeer>,
    onNameChange: (String) -> Unit,
    onNicknameChange: (String) -> Unit,
    onBeaconIdChange: (String) -> Unit,
    onMacChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onColorSelect: (Long) -> Unit,
    onFavoriteToggle: (Boolean) -> Unit,
    onPrefillPeer: (DetectedPeer) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    if (!formState.isOpen) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (formState.editingFriendId == null) "Add Friend Device" else "Edit Friend Device",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Enter your friend's WHOisnearME Beacon ID (recommended) and/or their Bluetooth MAC address.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (discoveredPeers.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Detected nearby — tap to autofill:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        discoveredPeers.take(6).forEach { peer ->
                            val label = buildString {
                                if (peer.advertisedName.isNotBlank()) append(peer.advertisedName).append(" • ")
                                if (peer.beaconId.isNotBlank()) append(peer.beaconId) else append(peer.macAddress)
                            }
                            AssistChip(
                                onClick = { onPrefillPeer(peer) },
                                label = { Text(text = label, style = MonoLabelStyle) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.BluetoothSearching,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = formState.name,
                onValueChange = onNameChange,
                label = { Text("Friend Name *") },
                placeholder = { Text("e.g. Maya Patel") },
                isError = formState.nameError != null,
                supportingText = formState.nameError?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("friend_name_input")
            )

            OutlinedTextField(
                value = formState.nickname,
                onValueChange = onNicknameChange,
                label = { Text("Nickname (Optional)") },
                placeholder = { Text("e.g. May / Roommate") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("friend_nickname_input")
            )

            OutlinedTextField(
                value = formState.beaconId,
                onValueChange = onBeaconIdChange,
                label = { Text("WHOisnearME Beacon ID") },
                placeholder = { Text("A7F2-9C14") },
                isError = formState.beaconIdError != null,
                supportingText = {
                    Text(
                        text = formState.beaconIdError
                            ?: "8 hex characters shown on your friend's My Device tab"
                    )
                },
                singleLine = true,
                textStyle = MonoLabelStyle,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("friend_beacon_input")
            )

            OutlinedTextField(
                value = formState.macAddress,
                onValueChange = onMacChange,
                label = { Text("Bluetooth MAC Address (Optional)") },
                placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                isError = formState.macError != null,
                supportingText = {
                    Text(
                        text = formState.macError
                            ?: "Optional fallback if your friend's hardware Bluetooth MAC is known"
                    )
                },
                singleLine = true,
                textStyle = MonoLabelStyle,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("friend_mac_input")
            )

            OutlinedTextField(
                value = formState.note,
                onValueChange = onNoteChange,
                label = { Text("Device Note (Optional)") },
                placeholder = { Text("e.g. Pixel 8 • Hostel Wing B") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("friend_note_input")
            )

            if (formState.generalError != null) {
                Text(
                    text = formState.generalError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Avatar Accent",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FriendAvatarColors.forEach { colorHex ->
                        val selected = formState.avatarColorHex == colorHex
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable { onColorSelect(colorHex) },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorHex))
                                    .then(
                                        if (selected) {
                                            Modifier.border(
                                                width = 2.5.dp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                shape = CircleShape
                                            )
                                        } else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Selected color",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pin as Favorite",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Keep this friend near the top of your Friends list",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = formState.isFavorite,
                    onCheckedChange = onFavoriteToggle,
                    modifier = Modifier.testTag("friend_favorite_switch")
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("friend_form_cancel_button")
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onSubmit,
                    enabled = !formState.isSaving,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("friend_form_save_button")
                ) {
                    Text(if (formState.editingFriendId == null) "Save Friend" else "Update")
                }
            }
        }
    }
}
