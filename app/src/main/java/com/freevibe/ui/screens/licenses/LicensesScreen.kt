package com.freevibe.ui.screens.licenses

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

data class OssLicense(
    val name: String,
    val url: String,
    val license: String,
    val description: String,
)

private val licenses = listOf(
    OssLicense("Kotlin", "https://github.com/JetBrains/kotlin", "Apache 2.0", "Programming language"),
    OssLicense("Jetpack Compose", "https://developer.android.com/jetpack/compose", "Apache 2.0", "Modern UI toolkit"),
    OssLicense("Material 3", "https://m3.material.io", "Apache 2.0", "Design system"),
    OssLicense("Hilt", "https://dagger.dev/hilt/", "Apache 2.0", "Dependency injection"),
    OssLicense("Room", "https://developer.android.com/training/data-storage/room", "Apache 2.0", "Local database"),
    OssLicense("Retrofit", "https://github.com/square/retrofit", "Apache 2.0", "HTTP client"),
    OssLicense("OkHttp", "https://github.com/square/okhttp", "Apache 2.0", "HTTP engine"),
    OssLicense("Moshi", "https://github.com/square/moshi", "Apache 2.0", "JSON parsing"),
    OssLicense("Coil", "https://github.com/coil-kt/coil", "Apache 2.0", "Image loading"),
    OssLicense("Media3 ExoPlayer", "https://github.com/androidx/media", "Apache 2.0", "Audio playback"),
    OssLicense("WorkManager", "https://developer.android.com/topic/libraries/architecture/workmanager", "Apache 2.0", "Background scheduling"),
    OssLicense("DataStore", "https://developer.android.com/topic/libraries/architecture/datastore", "Apache 2.0", "Persistent preferences"),
    OssLicense("Paging 3", "https://developer.android.com/topic/libraries/architecture/paging/v3-overview", "Apache 2.0", "Infinite scroll"),
    OssLicense("Glance", "https://developer.android.com/jetpack/compose/glance", "Apache 2.0", "App widgets"),
    OssLicense("Navigation Compose", "https://developer.android.com/jetpack/compose/navigation", "Apache 2.0", "Screen navigation"),
)

private val contentSources = listOf(
    OssLicense("Wallhaven", "https://wallhaven.cc/help/api", "Various per image", "Wallpaper source"),
    OssLicense("Lorem Picsum", "https://picsum.photos/", "Unsplash License", "Curated photos from Unsplash"),
    OssLicense("Bing Image of the Day", "https://www.bing.com", "Wallpaper use", "Daily curated photos"),
    OssLicense("Reddit", "https://www.reddit.com/dev/api/", "User-owned", "Wallpaper subreddits"),
    OssLicense("Internet Archive", "https://archive.org/", "Various CC / Public Domain", "Audio search & download"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Open Source Licenses") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item {
                Text(
                    "Libraries",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            items(licenses) { lic ->
                LicenseCard(lic)
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Content Sources",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            items(contentSources) { lic ->
                LicenseCard(lic)
            }
        }
    }
}

@Composable
private fun LicenseCard(lic: OssLicense) {
    val context = LocalContext.current
    Surface(
        onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(lic.url)))
        },
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(lic.name, style = MaterialTheme.typography.titleSmall)
                Text(lic.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(lic.license, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
