package com.kingslayer06.vox.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Mirrors the frontend Tailwind palette in tailwind.config.js. */
object VoxColors {
    val Bg = Color(0xFF0C0C10)
    val Panel = Color(0xFF15151B)
    val Panel2 = Color(0xFF1B1B22)
    val Line = Color(0xFF2A2A33)
    val Ink = Color(0xFFFFFFFF)
    val Muted = Color(0xFF8A8A95)
    val Accent = Color(0xFF8DEFC2)
    val Accent2 = Color(0xFF5DD0E5)
    val AccentInk = Color(0xFF0A1A12)
    val Warn = Color(0xFFFFC36B)
    val Bad = Color(0xFFFB7185)
    val Info = Color(0xFFA5B4FC)
}

@Composable
fun VoxTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = VoxColors.Accent,
        onPrimary = VoxColors.AccentInk,
        secondary = VoxColors.Accent2,
        background = VoxColors.Bg,
        onBackground = VoxColors.Ink,
        surface = VoxColors.Panel,
        onSurface = VoxColors.Ink,
        surfaceVariant = VoxColors.Panel2,
        outline = VoxColors.Line,
        error = VoxColors.Bad,
    )
    MaterialTheme(
        colorScheme = scheme,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(6.dp),
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(14.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
        typography = Typography(
            displayLarge = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold, color = VoxColors.Ink),
            headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = VoxColors.Ink),
            titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = VoxColors.Ink),
            bodyLarge = TextStyle(fontSize = 16.sp, color = VoxColors.Ink),
            bodyMedium = TextStyle(fontSize = 14.sp, color = VoxColors.Ink),
            bodySmall = TextStyle(fontSize = 12.sp, color = VoxColors.Muted),
            labelSmall = TextStyle(fontSize = 11.sp, color = VoxColors.Muted, fontWeight = FontWeight.Medium),
        ),
        content = content,
    )
}
