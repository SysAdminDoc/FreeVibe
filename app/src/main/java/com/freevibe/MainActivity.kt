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

private data class LaunchNavigation(
    val route: String? = null,
    val wallpaper: Wallpaper? = null,
    val token: Long = System.nanoTime(),
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var launchNavigation by mutableStateOf<LaunchNavigation?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        launchNavigation = parseLaunchNavigation(intent)
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
        launchNavigation = parseLaunchNavigation(intent)
    }

    private fun parseLaunchNavigation(intent: Intent?): LaunchNavigation? {
        if (intent == null) return null

        val wallpaperId = intent.getStringExtra("daily_wallpaper_id")
        val fullUrl = intent.getStringExtra("daily_wallpaper_url").orEmpty()
        val thumbnailUrl = intent.getStringExtra("daily_wallpaper_thumb").orEmpty().ifBlank { fullUrl }

        val wallpaper = if (!wallpaperId.isNullOrBlank() && fullUrl.isNotBlank()) {
            Wallpaper(
                id = wallpaperId,
                source = ContentSource.REDDIT,
                thumbnailUrl = thumbnailUrl,
                fullUrl = fullUrl,
                width = 0,
                height = 0,
                category = "Wallpaper of the Day",
            )
        } else {
            null
        }

        val route = wallpaper?.let { Screen.WallpaperDetail.createRoute(it) }
            ?: intent.getStringExtra("navigate_to")

        return if (route != null || wallpaper != null) {
            LaunchNavigation(route = route, wallpaper = wallpaper)
        } else {
            null
        }
    }
}
