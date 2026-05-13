package com.freevibe.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// ── Color palette: neutral AMOLED with brass, mist, and coral accents ─────

val Black = Color(0xFF050607)
val Surface = Color(0xFF0B0D10)
val SurfaceVariant = Color(0xFF171B20)
val SurfaceContainer = Color(0xFF111419)
val SurfaceContainerHigh = Color(0xFF1D232A)
val Outline = Color(0xFF3A414A)
val OutlineVariant = Color(0xFF262D35)

val Primary = Color(0xFFE7BE63)
val PrimaryContainer = Color(0xFF3B2A0C)
val Secondary = Color(0xFF8EDCE6)
val SecondaryContainer = Color(0xFF10343B)
val Tertiary = Color(0xFFFF8E72)
val TertiaryContainer = Color(0xFF482116)
val Error = Color(0xFFFF7B72)

val OnSurface = Color(0xFFF3F5F7)
val OnSurfaceVariant = Color(0xFFADB7C1)
val OnPrimary = Color(0xFF2A1D00)
val OnSecondary = Color(0xFF05141C)

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = Color(0xFFFFE9BF),
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = Color(0xFFD2F3FF),
    tertiary = Tertiary,
    onTertiary = Color(0xFF361108),
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

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF7A5811),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE6AE),
    onPrimaryContainer = Color(0xFF2C1C00),
    secondary = Color(0xFF1F6070),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCBEFF4),
    onSecondaryContainer = Color(0xFF001F29),
    tertiary = Color(0xFF9B4A35),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBD1),
    onTertiaryContainer = Color(0xFF3D0D02),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6F7F8),
    onBackground = Color(0xFF161A1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF161A1F),
    surfaceVariant = Color(0xFFE5E8EC),
    onSurfaceVariant = Color(0xFF4E5963),
    surfaceContainer = Color(0xFFF0F2F4),
    surfaceContainerHigh = Color(0xFFE7EAEE),
    outline = Color(0xFF77818B),
    outlineVariant = Color(0xFFCDD3D9),
)

@Composable
fun FreeVibeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = view.context
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = Shapes(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
            small = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        ),
        typography = Typography(
            headlineLarge = TextStyle(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 31.sp,
                letterSpacing = 0.sp,
            ),
            headlineMedium = TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                letterSpacing = 0.sp,
            ),
            headlineSmall = TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 21.sp,
                letterSpacing = 0.sp,
            ),
            titleLarge = TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 19.sp,
            ),
            titleMedium = TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
            ),
            titleSmall = TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            ),
            bodyLarge = TextStyle(
                fontSize = 15.sp,
                lineHeight = 23.sp,
            ),
            bodyMedium = TextStyle(
                fontSize = 13.5.sp,
                lineHeight = 20.sp,
            ),
            bodySmall = TextStyle(
                fontSize = 12.sp,
                lineHeight = 17.sp,
            ),
            labelLarge = TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                letterSpacing = 0.sp,
            ),
            labelMedium = TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                letterSpacing = 0.sp,
            ),
            labelSmall = TextStyle(
                fontSize = 11.sp,
                letterSpacing = 0.sp,
            ),
        ),
        content = content,
    )
}
