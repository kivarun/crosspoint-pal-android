package dev.crosspoint.pal.ui.theme

import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Spacing scale. Screens use these steps instead of ad-hoc dp values.
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

// Rounded but not bubbly: quiet corners suited to the paper aesthetic.
val PalShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
)
