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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PeopleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.entity.Friend
import com.example.ui.components.FriendProximityCard
import com.example.ui.viewmodel.FriendFilter
import com.example.ui.viewmodel.WhoIsNearMeUiState

@Composable
fun FriendsScreen(
    uiState: WhoIsNearMeUiState,
    onSearchQueryChange: (String) -> Unit,
    onFilterChange: (FriendFilter) -> Unit,
    onAddFriendClick: () -> Unit,
    onFriendClick: (Long) -> Unit,
    onToggleFavorite: (Friend) -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredFriends = remember(
        uiState.friendsWithProximity,
        uiState.searchQuery,
        uiState.friendFilter
    ) {
        val q = uiState.searchQuery.trim().lowercase()
        uiState.friendsWithProximity.filter { item ->
            val matchesFilter = when (uiState.friendFilter) {
                FriendFilter.ALL -> true
                FriendFilter.NEARBY -> item.isCurrentlyNearby
                FriendFilter.FAVORITES -> item.friend.isFavorite
            }
            val matchesQuery = q.isEmpty() ||
                item.friend.name.lowercase().contains(q) ||
                item.friend.nickname.lowercase().contains(q) ||
                item.friend.deviceIdentifier.lowercase().contains(q) ||
                item.friend.beaconId.lowercase().contains(q) ||
                item.friend.macAddress.lowercase().contains(q) ||
                item.friend.note.lowercase().contains(q)
            matchesFilter && matchesQuery
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.friends_title),
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Stored locally on your device across restarts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search name, Beacon ID, or MAC...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onSearchQueryChange("") },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("friends_search_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FriendFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = uiState.friendFilter == filter,
                            onClick = { onFilterChange(filter) },
                            label = {
                                val count = when (filter) {
                                    FriendFilter.ALL -> uiState.friendsWithProximity.size
                                    FriendFilter.NEARBY -> uiState.nearbyFriends.size
                                    FriendFilter.FAVORITES -> uiState.friendsWithProximity.count { it.friend.isFavorite }
                                }
                                Text("${filter.label} ($count)")
                            },
                            modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}")
                        )
                    }
                }
            }
        }

        if (filteredFriends.isEmpty()) {
            item {
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                        .padding(top = 8.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PeopleOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(4.dp)
                        )
                        Text(
                            text = if (uiState.friendsWithProximity.isEmpty()) {
                                stringResource(R.string.empty_friends_title)
                            } else {
                                "No matching friends"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (uiState.friendsWithProximity.isEmpty()) {
                                stringResource(R.string.empty_friends_subtitle)
                            } else {
                                "Try clearing your search query or switching filter tabs."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (uiState.friendsWithProximity.isEmpty()) {
                            Button(
                                onClick = onAddFriendClick,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("friends_empty_add_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Friend")
                            }
                        }
                    }
                }
            }
        } else {
            items(filteredFriends, key = { "friend_${it.friend.id}" }) { item ->
                FriendProximityCard(
                    item = item,
                    onClick = { onFriendClick(item.friend.id) },
                    onToggleFavorite = { onToggleFavorite(item.friend) },
                    modifier = Modifier.widthIn(max = 720.dp)
                )
            }
        }

        item { Spacer(modifier = Modifier.height(72.dp)) }
    }
}
