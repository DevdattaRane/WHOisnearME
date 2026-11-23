package com.example.ui.navigation

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.components.FriendFormSheet
import com.example.ui.screens.FriendDetailScreen
import com.example.ui.screens.FriendsScreen
import com.example.ui.screens.HistorySettingsScreen
import com.example.ui.screens.MyDeviceScreen
import com.example.ui.screens.RadarScreen
import com.example.ui.screens.RadarVisualizerScreen
import com.example.ui.viewmodel.MainViewModel

object NavRoutes {
    const val ROUTE_RADAR = "radar"
    const val ROUTE_RADAR_VISUALIZER = "radar_visualizer"
    const val ROUTE_FRIENDS = "friends"
    const val ROUTE_MY_DEVICE = "my_device"
    const val ROUTE_HISTORY = "history"
    const val ROUTE_FRIEND_DETAIL = "friend_detail"
    const val ARG_FRIEND_ID = "friendId"
}

private data class TopLevelTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val topLevelTabs = listOf(
    TopLevelTab(
        route = NavRoutes.ROUTE_RADAR,
        label = "Radar",
        selectedIcon = Icons.Filled.Radar,
        unselectedIcon = Icons.Outlined.Radar,
        testTag = "nav_tab_radar"
    ),
    TopLevelTab(
        route = NavRoutes.ROUTE_FRIENDS,
        label = "Friends",
        selectedIcon = Icons.Filled.People,
        unselectedIcon = Icons.Outlined.People,
        testTag = "nav_tab_friends"
    ),
    TopLevelTab(
        route = NavRoutes.ROUTE_MY_DEVICE,
        label = "My Device",
        selectedIcon = Icons.Filled.Devices,
        unselectedIcon = Icons.Outlined.Devices,
        testTag = "nav_tab_my_device"
    ),
    TopLevelTab(
        route = NavRoutes.ROUTE_HISTORY,
        label = "History",
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History,
        testTag = "nav_tab_history"
    )
)

@Composable
fun WhoIsNearMeAppShell(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.onPermissionsResult()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.setBackgroundServiceEnabled(context, true)
    }

    val alertNotificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setProximityNotificationsEnabled(granted)
        if (!granted) {
            viewModel.showSnackbar("Notification permission denied; haptic alerts remain active.")
        }
    }

    val onToggleProximityNotifications: (Boolean) -> Unit = { enabled ->
        if (enabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            alertNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setProximityNotificationsEnabled(enabled)
        }
    }

    val onToggleBackgroundService: (Boolean) -> Unit = { enabled ->
        if (enabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setBackgroundServiceEnabled(context, enabled)
        }
    }

    val bluetoothEnableLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        viewModel.onPermissionsResult()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onResumeRefresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeSnackbar()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isTopLevelRoute = topLevelTabs.any { tab ->
        currentDestination?.hierarchy?.any { it.route == tab.route } == true
    }

    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            floatingActionButton = {
                if (currentDestination?.route == NavRoutes.ROUTE_FRIENDS) {
                    FloatingActionButton(
                        onClick = { viewModel.openAddFriendForm(null) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("friends_fab_add")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Friend")
                    }
                }
            },
            bottomBar = {
                if (!useRail && isTopLevelRoute) {
                    NavigationBar {
                        topLevelTabs.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail && isTopLevelRoute) {
                    NavigationRail {
                        topLevelTabs.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = NavRoutes.ROUTE_RADAR,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(NavRoutes.ROUTE_RADAR) {
                        RadarScreen(
                            uiState = uiState,
                            onToggleScan = { viewModel.toggleScanning(context) },
                            onToggleBroadcast = viewModel::setBroadcastEnabled,
                            onToggleBackgroundService = onToggleBackgroundService,
                            onRequestPermissions = {
                                permissionLauncher.launch(viewModel.requiredPermissions())
                            },
                            onRequestEnableBluetooth = {
                                try {
                                    bluetoothEnableLauncher.launch(
                                        Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                                    )
                                } catch (_: Exception) {
                                    viewModel.showSnackbar("Open Android Bluetooth settings to turn on Bluetooth.")
                                }
                            },
                            onDismissBleError = viewModel::clearBleError,
                            onOpenFriendDetail = { friendId ->
                                navController.navigate("${NavRoutes.ROUTE_FRIEND_DETAIL}/$friendId")
                            },
                            onOpenAddFriend = { peer -> viewModel.openAddFriendForm(peer) },
                            onBlockPeer = viewModel::blockPeer,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onNavigateToMyDevice = { navigateToTab(NavRoutes.ROUTE_MY_DEVICE) },
                            onOpenVisualizer = {
                                navController.navigate(NavRoutes.ROUTE_RADAR_VISUALIZER)
                            }
                        )
                    }

                    composable(NavRoutes.ROUTE_RADAR_VISUALIZER) {
                        RadarVisualizerScreen(
                            uiState = uiState,
                            onBack = { navController.popBackStack() },
                            onToggleScan = { viewModel.toggleScanning(context) },
                            onAlertRangeChange = viewModel::setAlertRangeMeters,
                            onOpenFriendDetail = { friendId ->
                                navController.navigate("${NavRoutes.ROUTE_FRIEND_DETAIL}/$friendId")
                            }
                        )
                    }

                    composable(NavRoutes.ROUTE_FRIENDS) {
                        FriendsScreen(
                            uiState = uiState,
                            onSearchQueryChange = viewModel::updateSearchQuery,
                            onFilterChange = viewModel::updateFriendFilter,
                            onAddFriendClick = { viewModel.openAddFriendForm(null) },
                            onFriendClick = { friendId ->
                                navController.navigate("${NavRoutes.ROUTE_FRIEND_DETAIL}/$friendId")
                            },
                            onToggleFavorite = viewModel::toggleFavorite
                        )
                    }

                    composable(NavRoutes.ROUTE_MY_DEVICE) {
                        MyDeviceScreen(
                            uiState = uiState,
                            onUpdateDisplayName = viewModel::updateDisplayName,
                            onRegenerateBeaconId = viewModel::regenerateBeaconId,
                            onToggleBroadcast = viewModel::setBroadcastEnabled,
                            onCopiedMessage = viewModel::showSnackbar
                        )
                    }

                    composable(NavRoutes.ROUTE_HISTORY) {
                        HistorySettingsScreen(
                            uiState = uiState,
                            onAutoScanToggle = viewModel::setAutoScanOnLaunch,
                            onBroadcastToggle = viewModel::setBroadcastEnabled,
                            onBackgroundServiceToggle = onToggleBackgroundService,
                            onProximityNotificationsToggle = onToggleProximityNotifications,
                            onHapticFeedbackToggle = viewModel::setHapticFeedbackEnabled,
                            onTestProximityAlert = viewModel::sendTestProximityAlert,
                            onAlertRangeChange = viewModel::setAlertRangeMeters,
                            onShowUnknownBleToggle = viewModel::setShowUnknownBleDevices,
                            onOpenFriendDetail = { friendId ->
                                navController.navigate("${NavRoutes.ROUTE_FRIEND_DETAIL}/$friendId")
                            },
                            onBlockIdentifierManual = viewModel::blockIdentifierManual,
                            onUnblockDevice = viewModel::unblockDevice,
                            onClearHistory = viewModel::clearSightingsHistory,
                            onResetAllData = viewModel::clearAllLocalData
                        )
                    }

                    composable(
                        route = "${NavRoutes.ROUTE_FRIEND_DETAIL}/{${NavRoutes.ARG_FRIEND_ID}}",
                        arguments = listOf(
                            navArgument(NavRoutes.ARG_FRIEND_ID) { type = NavType.LongType }
                        )
                    ) { backStackEntry ->
                        val friendId = backStackEntry.arguments?.getLong(NavRoutes.ARG_FRIEND_ID) ?: -1L
                        val friendDetail by viewModel.observeFriendDetail(friendId)
                            .collectAsStateWithLifecycle(initialValue = null)
                        val friendSightings by viewModel.observeSightingsForFriend(friendId)
                            .collectAsStateWithLifecycle(initialValue = emptyList())

                        FriendDetailScreen(
                            friendWithProximity = friendDetail,
                            sightings = friendSightings,
                            onBack = { navController.popBackStack() },
                            onEdit = viewModel::openEditFriendForm,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onBlockFriend = viewModel::blockFriend,
                            onUnblockFriend = viewModel::unblockFriend,
                            onDelete = viewModel::deleteFriend
                        )
                    }
                }
            }
        }

        FriendFormSheet(
            formState = formState,
            discoveredPeers = uiState.unmatchedNearbyPeers,
            onNameChange = viewModel::updateFormName,
            onNicknameChange = viewModel::updateFormNickname,
            onBeaconIdChange = viewModel::updateFormBeaconId,
            onMacChange = viewModel::updateFormMac,
            onNoteChange = viewModel::updateFormNote,
            onColorSelect = viewModel::updateFormAvatarColor,
            onFavoriteToggle = viewModel::updateFormFavorite,
            onPrefillPeer = viewModel::prefillFormFromPeer,
            onDismiss = viewModel::closeFriendForm,
            onSubmit = viewModel::submitFriendForm
        )
    }
}
