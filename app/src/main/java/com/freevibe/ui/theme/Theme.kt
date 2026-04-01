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

// ── Color palette: nocturne premium with brass + mist accents ─────

val Black = Color(0xFF071017)
val Surface = Color(0xFF0C1720)
val SurfaceVariant = Color(0xFF152430)
val SurfaceContainer = Color(0xFF13222E)
val SurfaceContainerHigh = Color(0xFF1B2D3C)
val Outline = Color(0xFF314658)
val OutlineVariant = Color(0xFF243747)

val Primary = Color(0xFFF2C572)
val PrimaryContainer = Color(0xFF4D3910)
val Secondary = Color(0xFF9CD4E5)
val SecondaryContainer = Color(0xFF143744)
val Tertiary = Color(0xFFF39A83)
val TertiaryContainer = Color(0xFF4B241B)
val Error = Color(0xFFFF7B72)

val OnSurface = Color(0xFFF4F6F8)
val OnSurfaceVariant = Color(0xFFA8B6C3)
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
    primary = Color(0xFF8B640D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFEAB9),
    onPrimaryContainer = Color(0xFF2C1C00),
    secondary = Color(0xFF21586B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD0ECF5),
    onSecondaryContainer = Color(0xFF001F29),
    tertiary = Color(0xFF9D4D38),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBD1),
    onTertiaryContainer = Color(0xFF3D0D02),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF4F1EA),
    onBackground = Color(0xFF171B20),
    surface = Color(0xFFF8F5EF),
    onSurface = Color(0xFF171B20),
    surfaceVariant = Color(0xFFE8E1D5),
    onSurfaceVariant = Color(0xFF505A63),
    surfaceContainer = Color(0xFFF0EBE2),
    surfaceContainerHigh = Color(0xFFE7E0D6),
    outline = Color(0xFF77808A),
    outlineVariant = Color(0xFFD1C9BC),
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
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            small = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(30.dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(36.dp),
        ),
        typography = Typography(
            headlineLarge = TextStyle(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 31.sp,
                letterSpacing = (-0.8).sp,
            ),
            headlineMedium = TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                letterSpacing = (-0.4).sp,
            ),
            headlineSmall = TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 21.sp,
                letterSpacing = (-0.3).sp,
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
                letterSpacing = 0.2.sp,
            ),
            labelMedium = TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                letterSpacing = 0.2.sp,
            ),
            labelSmall = TextStyle(
                fontSize = 11.sp,
                letterSpacing = 0.3.sp,
            ),
        ),
        content = content,
    )
}
