package dev.crosspoint.pal.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import dev.crosspoint.pal.R

/** Root destinations of the application. One concept, one canonical route. */
enum class PalDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val outlinedIcon: ImageVector,
    val selectedIcon: ImageVector,
) {
    HOME("home", R.string.nav_home, Icons.Outlined.Home, Icons.Filled.Home),
    SEND("send", R.string.nav_send, Icons.Outlined.Send, Icons.Filled.Send),
    NOTIFICATIONS(
        "notifications",
        R.string.nav_notifications,
        Icons.Outlined.Notifications,
        Icons.Filled.Notifications,
    ),
    SETTINGS("settings", R.string.nav_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
}
