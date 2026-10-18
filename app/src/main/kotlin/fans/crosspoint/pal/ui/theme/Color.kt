package fans.crosspoint.pal.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Light theme: warm paper surfaces, ink typography, almost monochrome.
private val Paper = Color(0xFFFAF6EE)
private val PaperRaised = Color(0xFFF6F1E7)
private val PaperContainer = Color(0xFFEFE9DC)
private val PaperContainerHigh = Color(0xFFE7E0D0)
private val Ink = Color(0xFF211D17)
private val InkSecondary = Color(0xFF4C463C)
private val OutlineWarm = Color(0xFFD3CCBC)
private val OutlineStrong = Color(0xFF847D6E)
private val PrimaryInk = Color(0xFF2B2822)
private val OnPrimaryPaper = Color(0xFFFAF6EE)
private val SuccessMuted = Color(0xFF4E7A5A)
private val WarningMuted = Color(0xFFA0762E)
private val ErrorBrick = Color(0xFF9C3F26)

// Dark theme: warm charcoal, muted light-gray text, subdued accents.
private val Charcoal = Color(0xFF161513)
private val CharcoalRaised = Color(0xFF1E1C19)
private val CharcoalContainer = Color(0xFF24211C)
private val CharcoalContainerHigh = Color(0xFF2E2A24)
private val LightInk = Color(0xFFE8E3D9)
private val LightInkSecondary = Color(0xFFB7B0A2)
private val OutlineDark = Color(0xFF3E3A32)
private val OutlineDarkStrong = Color(0xFF6C665A)
private val PrimaryPaper = Color(0xFFDCD4C4)
private val OnPrimaryCharcoal = Color(0xFF26221C)
private val SuccessMutedDark = Color(0xFF93B89D)
private val WarningMutedDark = Color(0xFFC9A261)
private val ErrorClayDark = Color(0xFFE2A18A)

val LightColors: ColorScheme = lightColorScheme(
    primary = PrimaryInk,
    onPrimary = OnPrimaryPaper,
    primaryContainer = PaperContainer,
    onPrimaryContainer = Ink,
    secondary = InkSecondary,
    onSecondary = Paper,
    secondaryContainer = PaperContainer,
    onSecondaryContainer = Ink,
    tertiary = Color(0xFF4A5448),
    onTertiary = Paper,
    tertiaryContainer = Color(0xFFDDE3D8),
    onTertiaryContainer = Color(0xFF2C342B),
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperContainer,
    onSurfaceVariant = InkSecondary,
    surfaceDim = Color(0xFFE2DAC8),
    surfaceBright = Color(0xFFFDFBF4),
    surfaceContainerLowest = Color(0xFFFEFCF7),
    surfaceContainerLow = Color(0xFFF8F3EA),
    surfaceContainer = PaperRaised,
    surfaceContainerHigh = PaperContainer,
    surfaceContainerHighest = PaperContainerHigh,
    outline = OutlineStrong,
    outlineVariant = OutlineWarm,
    inverseSurface = Color(0xFF373128),
    inverseOnSurface = Color(0xFFF6F1E7),
    inversePrimary = Color(0xFFE0D8C7),
    error = ErrorBrick,
    onError = Color(0xFFFDF4EE),
    errorContainer = Color(0xFFF4DCD3),
    onErrorContainer = Color(0xFF67230F),
    scrim = Color(0xFF000000),
)

val DarkColors: ColorScheme = darkColorScheme(
    primary = PrimaryPaper,
    onPrimary = OnPrimaryCharcoal,
    primaryContainer = CharcoalContainerHigh,
    onPrimaryContainer = LightInk,
    secondary = LightInkSecondary,
    onSecondary = Charcoal,
    secondaryContainer = CharcoalContainer,
    onSecondaryContainer = LightInk,
    tertiary = Color(0xFFB3BEA8),
    onTertiary = Color(0xFF232A21),
    tertiaryContainer = Color(0xFF3A4033),
    onTertiaryContainer = Color(0xFFD9E2CE),
    background = Charcoal,
    onBackground = LightInk,
    surface = Charcoal,
    onSurface = LightInk,
    surfaceVariant = CharcoalContainer,
    onSurfaceVariant = LightInkSecondary,
    surfaceDim = Color(0xFF161513),
    surfaceBright = Color(0xFF3B382F),
    surfaceContainerLowest = Color(0xFF110F0D),
    surfaceContainerLow = CharcoalRaised,
    surfaceContainer = Color(0xFF23201B),
    surfaceContainerHigh = CharcoalContainerHigh,
    surfaceContainerHighest = Color(0xFF39352C),
    outline = OutlineDarkStrong,
    outlineVariant = OutlineDark,
    inverseSurface = Color(0xFFE8E3D9),
    inverseOnSurface = Color(0xFF2B2822),
    inversePrimary = Color(0xFF423D33),
    error = ErrorClayDark,
    onError = Color(0xFF441A0D),
    errorContainer = Color(0xFF5C2A1A),
    onErrorContainer = Color(0xFFF4DCD3),
    scrim = Color(0xFF000000),
)

/**
 * Semantic status colors used by the companion state indicators.
 * These mean connected, attention, and danger; they are never decorative.
 */
data class PalStatusColors(
    val connected: Color,
    val warning: Color,
    val destructive: Color,
)

val LightStatusColors = PalStatusColors(
    connected = SuccessMuted,
    warning = WarningMuted,
    destructive = ErrorBrick,
)

val DarkStatusColors = PalStatusColors(
    connected = SuccessMutedDark,
    warning = WarningMutedDark,
    destructive = ErrorClayDark,
)
