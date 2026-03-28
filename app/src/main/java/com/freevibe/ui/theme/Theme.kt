package com.freevibe.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// ── Color palette: deep dark with cyan/purple accents ─────────────

val Black = Color(0xFF000000)
val Surface = Color(0xFF0A0A0F)
val SurfaceVariant = Color(0xFF12121A)
val SurfaceContainer = Color(0xFF1A1A24)
val SurfaceContainerHigh = Color(0xFF222230)
val Outline = Color(0xFF2A2A3A)
val OutlineVariant = Color(0xFF1E1E2E)

val Primary = Color(0xFF7C5CFC)           // Vibrant purple
val PrimaryContainer = Color(0xFF2A1F5E)
val Secondary = Color(0xFF00D4AA)          // Teal/cyan
val SecondaryContainer = Color(0xFF003D32)
val Tertiary = Color(0xFFFF6B9D)           // Pink accent
val TertiaryContainer = Color(0xFF4A1530)
val Error = Color(0xFFFF5252)

val OnSurface = Color(0xFFE8E8F0)
val OnSurfaceVariant = Color(0xFF9898AA)
val OnPrimary = Color(0xFFFFFFFF)
val OnSecondary = Color(0xFF000000)

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = Color(0xFFCDBDFF),
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = Color(0xFF70FFDA),
    tertiary = Tertiary,
    tertiaryContainer = TertiaryContainer,
    error = Error,
    background = Black,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceVariant = SurfaceVariant,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    outline = Outline,
    outlineVariant = OutlineVariant,
)

@Composable
fun FreeVibeTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = Black.toArgb()
            window.navigationBarColor = Black.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(
            headlineLarge = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                letterSpacing = (-0.5).sp,
            ),
            headlineMedium = TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
            ),
            titleLarge = TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            ),
            titleMedium = TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
            ),
            bodyLarge = TextStyle(
                fontSize = 15.sp,
                lineHeight = 22.sp,
            ),
            bodyMedium = TextStyle(
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
            labelLarge = TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                letterSpacing = 0.5.sp,
            ),
            labelSmall = TextStyle(
                fontSize = 11.sp,
                color = OnSurfaceVariant,
            ),
        ),
        content = content,
    )
}
