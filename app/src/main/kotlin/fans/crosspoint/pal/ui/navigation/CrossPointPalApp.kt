package fans.crosspoint.pal.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import fans.crosspoint.pal.AppContainer
import fans.crosspoint.pal.R
import fans.crosspoint.pal.ui.home.HomeEventsEffect
import fans.crosspoint.pal.ui.home.HomeScreen
import fans.crosspoint.pal.ui.home.HomeViewModel
import fans.crosspoint.pal.ui.notifications.NotificationsScreen
import fans.crosspoint.pal.ui.notifications.NotificationsViewModel
import fans.crosspoint.pal.ui.send.SendEventsEffect
import fans.crosspoint.pal.ui.send.SendScreen
import fans.crosspoint.pal.ui.send.SendViewModel
import fans.crosspoint.pal.ui.settings.SettingsEventsEffect
import fans.crosspoint.pal.ui.settings.SettingsScreen
import fans.crosspoint.pal.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

/**
 * Application root: single navigation host, single snackbar host, bottom
 * navigation over the four root destinations.
 */
@Composable
fun CrossPointPalApp(container: AppContainer) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val onShowSnackbar: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            PalNavigationBar(
                currentRoute = currentRoute,
                onSelectDestination = { destination ->
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
    ) { contentPadding ->
        PalNavHost(
            navController = navController,
            container = container,
            onShowSnackbar = onShowSnackbar,
            modifier = Modifier.padding(contentPadding),
        )
    }
}

@Composable
private fun PalNavigationBar(
    currentRoute: String?,
    onSelectDestination: (PalDestination) -> Unit,
) {
    NavigationBar {
        PalDestination.entries.forEach { destination ->
            val selected = currentRoute == destination.route
            NavigationBarItem(
                selected = selected,
                onClick = { onSelectDestination(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.outlinedIcon,
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(destination.labelRes)) },
            )
        }
    }
}

@Composable
private fun PalNavHost(
    navController: androidx.navigation.NavHostController,
    container: AppContainer,
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = PalDestination.HOME.route,
        modifier = modifier,
    ) {
        composable(PalDestination.HOME.route) {
            val viewModel: HomeViewModel = viewModel(
                factory = remember {
                    viewModelFactory {
                        initializer { HomeViewModel(container.connectionManager) }
                    }
                },
            )
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            HomeScreen(
                state = state,
                requiredPermissions = viewModel.requiredPermissions,
                onSendToPal = {
                    navController.navigate(PalDestination.SEND.route) { launchSingleTop = true }
                },
                onConnectPressed = viewModel::onConnectPressed,
                onDeviceSelected = viewModel::onDeviceSelected,
                onCancelScan = viewModel::cancelScan,
                onReconnectPressed = viewModel::onReconnectPressed,
                onDisconnectPressed = viewModel::onDisconnectPressed,
                onPermissionsResult = viewModel::onPermissionsResult,
            )
            HomeEventsEffect(events = viewModel.events, onError = onShowSnackbar)
        }

        composable(PalDestination.SEND.route) {
            val viewModel: SendViewModel = viewModel(
                factory = remember {
                    viewModelFactory {
                        initializer { SendViewModel(container.connectionManager) }
                    }
                },
            )
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            SendScreen(
                state = state,
                onTitleChange = viewModel::onTitleChange,
                onBodyChange = viewModel::onBodyChange,
                onClear = viewModel::clear,
                onSend = viewModel::send,
            )
            SendEventsEffect(
                events = viewModel.events,
                onContentSent = onShowSnackbar,
                onNoDevice = onShowSnackbar,
                onSendFailed = onShowSnackbar,
            )
        }

        composable(PalDestination.NOTIFICATIONS.route) {
            val viewModel: NotificationsViewModel = viewModel(
                factory = remember {
                    viewModelFactory {
                        initializer { NotificationsViewModel(container.demoNotificationState) }
                    }
                },
            )
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val syncedMessage = stringResource(R.string.notifications_synced_message)
            NotificationsScreen(
                state = state,
                onMasterToggle = viewModel::onMasterToggle,
                onSourceToggle = viewModel::onSourceToggle,
                onSyncNow = {
                    viewModel.syncNow()
                    onShowSnackbar(syncedMessage)
                },
            )
        }

        composable(PalDestination.SETTINGS.route) {
            val viewModel: SettingsViewModel = viewModel(
                factory = remember {
                    viewModelFactory {
                        initializer {
                            SettingsViewModel(
                                themeModeStore = container.themeModeStore,
                                connectionManager = container.connectionManager,
                            )
                        }
                    }
                },
            )
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            SettingsScreen(
                state = state,
                onThemeModeSelected = viewModel::onThemeModeSelected,
                onDisconnectPressed = viewModel::onDisconnectPressed,
                onForgetRequested = viewModel::onForgetRequested,
                onForgetConfirmed = viewModel::onForgetConfirmed,
                onForgetDismissed = viewModel::onForgetDismissed,
                onOpenNotifications = {
                    navController.navigate(PalDestination.NOTIFICATIONS.route) {
                        launchSingleTop = true
                    }
                },
            )
            SettingsEventsEffect(
                events = viewModel.events,
                onForgetCompleted = onShowSnackbar,
                onForgetNeedsSystemSettings = onShowSnackbar,
            )
        }
    }
}
