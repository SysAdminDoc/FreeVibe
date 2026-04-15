package com.freevibe

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import com.freevibe.ui.FreeVibeRoot
import com.freevibe.ui.navigation.Screen
import com.freevibe.ui.theme.FreeVibeTheme
import dagger.hilt.android.AndroidEntryPoint

internal data class LaunchNavigation(
    val route: String? = null,
    val wallpaper: Wallpaper? = null,
    val token: Long = System.nanoTime(),
)

private const val EXTRA_DAILY_WALLPAPER_ID = "daily_wallpaper_id"
private const val EXTRA_DAILY_WALLPAPER_URL = "daily_wallpaper_url"
private const val EXTRA_DAILY_WALLPAPER_THUMB = "daily_wallpaper_thumb"
private const val EXTRA_DAILY_WALLPAPER_SOURCE = "daily_wallpaper_source"
private const val EXTRA_DAILY_WALLPAPER_WIDTH = "daily_wallpaper_width"
private const val EXTRA_DAILY_WALLPAPER_HEIGHT = "daily_wallpaper_height"
private const val EXTRA_NAVIGATE_TO = "navigate_to"

internal fun consumeLaunchNavigation(intent: Intent?): LaunchNavigation? {
    val navigation = parseLaunchNavigation(intent)
    intent?.removeExtra(EXTRA_DAILY_WALLPAPER_ID)
    intent?.removeExtra(EXTRA_DAILY_WALLPAPER_URL)
    intent?.removeExtra(EXTRA_DAILY_WALLPAPER_THUMB)
    intent?.removeExtra(EXTRA_DAILY_WALLPAPER_SOURCE)
    intent?.removeExtra(EXTRA_DAILY_WALLPAPER_WIDTH)
    intent?.removeExtra(EXTRA_DAILY_WALLPAPER_HEIGHT)
    intent?.removeExtra(EXTRA_NAVIGATE_TO)
    return navigation
}

internal fun shouldHandleInitialLaunchNavigation(savedInstanceState: Bundle?): Boolean =
    savedInstanceState == null

internal fun buildLaunchNavigation(
    route: String? = null,
    wallpaperId: String? = null,
    fullUrl: String = "",
    thumbnailUrl: String = "",
    sourceName: String? = null,
    width: Int = 0,
    height: Int = 0,
): LaunchNavigation? {
    val wallpaper = buildLaunchWallpaper(
        wallpaperId = wallpaperId,
        fullUrl = fullUrl,
        thumbnailUrl = thumbnailUrl,
        sourceName = sourceName,
        width = width,
        height = height,
    )

    val resolvedRoute = wallpaper?.let { Screen.WallpaperDetail.createRoute(it) } ?: route
    return if (resolvedRoute != null || wallpaper != null) {
        LaunchNavigation(route = resolvedRoute, wallpaper = wallpaper)
    } else {
        null
    }
}

private fun isAllowedLaunchUrl(url: String): Boolean {
    val scheme = android.net.Uri.parse(url).scheme?.lowercase(java.util.Locale.ROOT)
    return scheme == "https"
}

internal fun buildLaunchWallpaper(
    wallpaperId: String? = null,
    fullUrl: String = "",
    thumbnailUrl: String = "",
    sourceName: String? = null,
    width: Int = 0,
    height: Int = 0,
): Wallpaper? {
    val normalizedThumb = thumbnailUrl.ifBlank { fullUrl }
    return if (!wallpaperId.isNullOrBlank() && fullUrl.isNotBlank() && isAllowedLaunchUrl(fullUrl)) {
        Wallpaper(
            id = wallpaperId,
            source = sourceName
                ?.let { name ->
                    runCatching { ContentSource.valueOf(name) }.getOrDefault(ContentSource.REDDIT)
                }
                ?: ContentSource.REDDIT,
            thumbnailUrl = normalizedThumb,
            fullUrl = fullUrl,
            width = width,
            height = height,
            category = "Wallpaper of the Day",
        )
    } else {
        null
    }
}

internal fun parseLaunchNavigation(intent: Intent?): LaunchNavigation? {
    if (intent == null) return null

    return buildLaunchNavigation(
        route = intent.getStringExtra(EXTRA_NAVIGATE_TO),
        wallpaperId = intent.getStringExtra(EXTRA_DAILY_WALLPAPER_ID),
        fullUrl = intent.getStringExtra(EXTRA_DAILY_WALLPAPER_URL).orEmpty(),
        thumbnailUrl = intent.getStringExtra(EXTRA_DAILY_WALLPAPER_THUMB).orEmpty(),
        sourceName = intent.getStringExtra(EXTRA_DAILY_WALLPAPER_SOURCE),
        width = intent.getIntExtra(EXTRA_DAILY_WALLPAPER_WIDTH, 0),
        height = intent.getIntExtra(EXTRA_DAILY_WALLPAPER_HEIGHT, 0),
    )
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var launchNavigation by mutableStateOf<LaunchNavigation?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        launchNavigation = if (shouldHandleInitialLaunchNavigation(savedInstanceState)) {
            consumeLaunchNavigation(intent)
        } else {
            null
        }
        setContent {
            FreeVibeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    FreeVibeRoot(
                        initialNavigateTo = launchNavigation?.route,
                        initialWallpaper = launchNavigation?.wallpaper,
                        navigationToken = launchNavigation?.token ?: 0L,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchNavigation = consumeLaunchNavigation(intent)
    }
}
