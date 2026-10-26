package com.danitube.app.ui.theme

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

val YtRed = Color(0xFFFF2B2B)
val Teal = Color(0xFF3EA6A0)
val Sunny = Color(0xFFFFC145)

private val colors = darkColorScheme(
    primary = YtRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5C1414),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF123F3C),
    onSecondaryContainer = Color(0xFFBFEFEA),
    tertiary = Sunny,
    background = Color(0xFF0F0F0F),
    onBackground = Color.White,
    surface = Color(0xFF212121),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2D2D2D),
    onSurfaceVariant = Color(0xFFAAAAAA),
    outline = Color(0xFF4A4A4A),
    error = Color(0xFFFF6B6B),
)

private val shapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

private val typography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 32.sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium),
    )
}

/** Dark, red-accented look on purpose: it should feel like the video app she already knows. */
@Composable
fun DaniTubeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, shapes = shapes, typography = typography, content = content)
}
