package fans.crosspoint.pal.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalPalStatusColors = staticCompositionLocalOf { LightStatusColors }

/** Status colors of the active [CrossPointPalTheme]. */
val MaterialTheme.palStatusColors: PalStatusColors
    @Composable get() = LocalPalStatusColors.current

