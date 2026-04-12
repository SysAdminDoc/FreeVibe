package com.freevibe.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import android.net.Uri
import androidx.compose.ui.graphics.vector.ImageVector
import com.freevibe.data.model.Sound
import com.freevibe.data.model.Wallpaper

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val destinationPattern: String = route,
) {
    fun matchesDestination(destinationRoute: String?): Boolean =
        destinationRoute == route || destinationRoute == destinationPattern

    // ── Bottom nav tabs ───────────────────────────────────────────
    data object Wallpapers : Screen(
        route = "wallpapers",
        title = "Wallpapers",
        icon = Icons.Outlined.Wallpaper,
        selectedIcon = Icons.Filled.Wallpaper,
        destinationPattern = "wallpapers?query={query}&color={color}&similarId={similarId}&similarSource={similarSource}&similarFullUrl={similarFullUrl}",
    ) {
        fun createRoute(
            query: String? = null,
            color: String? = null,
            similarId: String? = null,
            similarSource: String? = null,
            similarFullUrl: String? = null,
        ): String {
            val params = buildList {
                query?.takeIf { it.isNotBlank() }?.let { add("query=${Uri.encode(it)}") }
                color?.takeIf { it.isNotBlank() }?.let { add("color=${Uri.encode(it)}") }
                similarId?.takeIf { it.isNotBlank() }?.let { add("similarId=${Uri.encode(it)}") }
                similarSource?.takeIf { it.isNotBlank() }?.let { add("similarSource=${Uri.encode(it)}") }
                similarFullUrl?.takeIf { it.isNotBlank() }?.let { add("similarFullUrl=${Uri.encode(it)}") }
            }
            return if (params.isEmpty()) route else "$route?${params.joinToString("&")}"
        }

        fun createSimilarRoute(wallpaper: Wallpaper): String =
            createRoute(
                similarId = wallpaper.id,
                similarSource = wallpaper.source.name,
                similarFullUrl = wallpaper.fullUrl,
            )
    }
    data object VideoWallpapers : Screen(
        route = "video_wallpapers",
        title = "Videos",
        icon = Icons.Outlined.VideoLibrary,
        selectedIcon = Icons.Filled.VideoLibrary,
    )
    data object Sounds : Screen(
        route = "sounds",
        title = "Sounds",
        icon = Icons.Outlined.MusicNote,
        selectedIcon = Icons.Filled.MusicNote,
        destinationPattern = "sounds?query={query}",
    ) {
        fun createRoute(query: String? = null): String {
            return if (query.isNullOrBlank()) route
            else "$route?query=${Uri.encode(query)}"
        }
    }
    data object Favorites : Screen(
        route = "favorites",
        title = "Favorites",
        icon = Icons.Outlined.FavoriteBorder,
        selectedIcon = Icons.Filled.Favorite,
    )
    data object Settings : Screen(
        route = "settings",
        title = "Settings",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Filled.Settings,
    )

    // ── Wallpaper detail + editor ─────────────────────────────────
    data object WallpaperDetail : Screen(
        route = "wallpaper/{id}",
        title = "Preview",
        icon = Icons.Filled.Wallpaper,
        selectedIcon = Icons.Filled.Wallpaper,
        destinationPattern = "wallpaper/{id}?source={source}&thumbnailUrl={thumbnailUrl}&fullUrl={fullUrl}&width={width}&height={height}",
    ) {
        fun createRoute(id: String) = "wallpaper/${Uri.encode(id)}"

        fun createRoute(wallpaper: Wallpaper): String {
            val queryParams = buildList {
                add("source=${Uri.encode(wallpaper.source.name)}")
                add("thumbnailUrl=${Uri.encode(wallpaper.thumbnailUrl)}")
                add("fullUrl=${Uri.encode(wallpaper.fullUrl)}")
                add("width=${wallpaper.width}")
                add("height=${wallpaper.height}")
            }.joinToString("&")
            return "${createRoute(wallpaper.id)}?$queryParams"
        }
    }
    data object WallpaperEditor : Screen(
        route = "wallpaper_editor/{id}",
        title = "Edit",
        icon = Icons.Filled.Edit,
        selectedIcon = Icons.Filled.Edit,
        destinationPattern = "wallpaper_editor/{id}?source={source}&thumbnailUrl={thumbnailUrl}&fullUrl={fullUrl}&width={width}&height={height}",
    ) {
        fun createRoute(id: String) = "wallpaper_editor/${Uri.encode(id)}"

        fun createRoute(wallpaper: Wallpaper): String {
            val queryParams = buildList {
                add("source=${Uri.encode(wallpaper.source.name)}")
                add("thumbnailUrl=${Uri.encode(wallpaper.thumbnailUrl)}")
                add("fullUrl=${Uri.encode(wallpaper.fullUrl)}")
                add("width=${wallpaper.width}")
                add("height=${wallpaper.height}")
            }.joinToString("&")
            return "${createRoute(wallpaper.id)}?$queryParams"
        }
    }

    // ── Sound detail + editor ─────────────────────────────────────
    data object SoundDetail : Screen(
        route = "sound/{id}",
        title = "Sound",
        icon = Icons.Filled.MusicNote,
        selectedIcon = Icons.Filled.MusicNote,
        destinationPattern = "sound/{id}?source={source}&name={name}&previewUrl={previewUrl}&downloadUrl={downloadUrl}",
    ) {
        fun createRoute(id: String) = "sound/${Uri.encode(id)}"

        fun createRoute(sound: Sound): String {
            val queryParams = buildList {
                add("source=${Uri.encode(sound.source.name)}")
                add("name=${Uri.encode(sound.name)}")
                add("previewUrl=${Uri.encode(sound.previewUrl)}")
                add("downloadUrl=${Uri.encode(sound.downloadUrl)}")
            }.joinToString("&")
            return "${createRoute(sound.id)}?$queryParams"
        }
    }
    data object SoundEditor : Screen(
        route = "sound_editor?soundId={soundId}",
        title = "Edit Sound",
        icon = Icons.Filled.ContentCut,
        selectedIcon = Icons.Filled.ContentCut,
        destinationPattern = "sound_editor?soundId={soundId}&source={source}&name={name}&previewUrl={previewUrl}&downloadUrl={downloadUrl}",
    ) {
        fun createRoute(soundId: String? = null) =
            soundId?.let { "sound_editor?soundId=${Uri.encode(it)}" } ?: "sound_editor"

        fun createRoute(sound: Sound): String {
            val queryParams = buildList {
                add("soundId=${Uri.encode(sound.id)}")
                add("source=${Uri.encode(sound.source.name)}")
                add("name=${Uri.encode(sound.name)}")
                add("previewUrl=${Uri.encode(sound.previewUrl)}")
                add("downloadUrl=${Uri.encode(sound.downloadUrl)}")
            }.joinToString("&")
            return "sound_editor?$queryParams"
        }
    }

    // ── Preview (mock lock + home screen over the wallpaper) ────
    data object WallpaperPreview : Screen(
        route = "wallpaper_preview/{id}",
        title = "Preview",
        icon = Icons.Filled.Visibility,
        selectedIcon = Icons.Filled.Visibility,
        destinationPattern = "wallpaper_preview/{id}?source={source}&thumbnailUrl={thumbnailUrl}&fullUrl={fullUrl}&width={width}&height={height}",
    ) {
        fun createRoute(id: String) = "wallpaper_preview/${Uri.encode(id)}"

        fun createRoute(wallpaper: Wallpaper): String {
            val queryParams = buildList {
                add("source=${Uri.encode(wallpaper.source.name)}")
                add("thumbnailUrl=${Uri.encode(wallpaper.thumbnailUrl)}")
                add("fullUrl=${Uri.encode(wallpaper.fullUrl)}")
                add("width=${wallpaper.width}")
                add("height=${wallpaper.height}")
            }.joinToString("&")
            return "${createRoute(wallpaper.id)}?$queryParams"
        }
    }

    // ── Crop/Position ─────────────────────────────────────────────
    data object WallpaperCrop : Screen(
        route = "wallpaper_crop/{id}",
        title = "Crop",
        icon = Icons.Filled.Crop,
        selectedIcon = Icons.Filled.Crop,
        destinationPattern = "wallpaper_crop/{id}?source={source}&thumbnailUrl={thumbnailUrl}&fullUrl={fullUrl}&width={width}&height={height}",
    ) {
        fun createRoute(id: String) = "wallpaper_crop/${Uri.encode(id)}"

        fun createRoute(wallpaper: Wallpaper): String {
            val queryParams = buildList {
                add("source=${Uri.encode(wallpaper.source.name)}")
                add("thumbnailUrl=${Uri.encode(wallpaper.thumbnailUrl)}")
                add("fullUrl=${Uri.encode(wallpaper.fullUrl)}")
                add("width=${wallpaper.width}")
                add("height=${wallpaper.height}")
            }.joinToString("&")
            return "${createRoute(wallpaper.id)}?$queryParams"
        }
    }

    // ── Contact Ringtone Picker ───────────────────────────────────
    data object ContactPicker : Screen(
        route = "contact_picker/{soundId}",
        title = "Pick Contact",
        icon = Icons.Filled.Contacts,
        selectedIcon = Icons.Filled.Contacts,
        destinationPattern = "contact_picker/{soundId}?source={source}&name={name}&previewUrl={previewUrl}&downloadUrl={downloadUrl}",
    ) {
        fun createRoute(soundId: String) = "contact_picker/${Uri.encode(soundId)}"

        fun createRoute(sound: Sound): String {
            val queryParams = buildList {
                add("source=${Uri.encode(sound.source.name)}")
                add("name=${Uri.encode(sound.name)}")
                add("previewUrl=${Uri.encode(sound.previewUrl)}")
                add("downloadUrl=${Uri.encode(sound.downloadUrl)}")
            }.joinToString("&")
            return "${createRoute(sound.id)}?$queryParams"
        }
    }

    // ── Onboarding ────────────────────────────────────────────────
    data object Onboarding : Screen(
        route = "onboarding",
        title = "Setup",
        icon = Icons.Filled.PlayArrow,
        selectedIcon = Icons.Filled.PlayArrow,
    )

    // ── Downloads ─────────────────────────────────────────────────
    data object Downloads : Screen(
        route = "downloads",
        title = "Downloads",
        icon = Icons.Filled.Download,
        selectedIcon = Icons.Filled.Download,
    )

    // ── Categories ────────────────────────────────────────────────
    data object Categories : Screen(
        route = "categories",
        title = "Categories",
        icon = Icons.Filled.Category,
        selectedIcon = Icons.Filled.Category,
    )

    // ── Licenses ──────────────────────────────────────────────────
    data object Licenses : Screen(
        route = "licenses",
        title = "Licenses",
        icon = Icons.Filled.Description,
        selectedIcon = Icons.Filled.Description,
    )

    // ── Collections ─────────────────────────────────────────────
    data object Collections : Screen(
        route = "collections",
        title = "Collections",
        icon = Icons.Filled.Folder,
        selectedIcon = Icons.Filled.Folder,
    )

    // ── Wallpaper History ───────────────────────────────────────
    data object WallpaperHistory : Screen(
        route = "wallpaper_history",
        title = "Wallpaper History",
        icon = Icons.Filled.History,
        selectedIcon = Icons.Filled.History,
    )

    companion object {
        val bottomNavItems = listOf(Wallpapers, VideoWallpapers, Sounds, Favorites, Settings)
    }
}
