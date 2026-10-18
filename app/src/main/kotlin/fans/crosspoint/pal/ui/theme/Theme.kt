package fans.crosspoint.pal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * CrossPoint Pal application theme.
 *
 * The visual identity is a single "e-ink paper" language expressed in two
 * schemes: warm paper in light mode, warm charcoal in dark mode. Dynamic
 * color is intentionally not used; the palette is part of the product design.
 */
@Composable
fun CrossPointPalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val statusColors = if (darkTheme) DarkStatusColors else LightStatusColors

    CompositionLocalProvider(LocalPalStatusColors provides statusColors) {
        MaterialTheme(
            colorScheme = colors,
            typography = PalTypography,
            shapes = PalShapes,
            content = content,
        )
    }
}
