package com.freevibe.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    // ── Bottom nav tabs ───────────────────────────────────────────
    data object Wallpapers : Screen(
        route = "wallpapers",
        title = "Wallpapers",
        icon = Icons.Outlined.Wallpaper,
        selectedIcon = Icons.Filled.Wallpaper,
    )
    data object Sounds : Screen(
        route = "sounds",
        title = "Sounds",
        icon = Icons.Outlined.MusicNote,
        selectedIcon = Icons.Filled.MusicNote,
    )
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
    ) {
        fun createRoute(id: String) = "wallpaper/$id"
    }
    data object WallpaperEditor : Screen(
        route = "wallpaper_editor",
        title = "Edit",
        icon = Icons.Filled.Edit,
        selectedIcon = Icons.Filled.Edit,
    )

    // ── Sound detail + editor ─────────────────────────────────────
    data object SoundDetail : Screen(
        route = "sound/{id}",
        title = "Sound",
        icon = Icons.Filled.MusicNote,
        selectedIcon = Icons.Filled.MusicNote,
    ) {
        fun createRoute(id: String) = "sound/$id"
    }
    data object SoundEditor : Screen(
        route = "sound_editor",
        title = "Edit Sound",
        icon = Icons.Filled.ContentCut,
        selectedIcon = Icons.Filled.ContentCut,
    )

    // ── Crop/Position ─────────────────────────────────────────────
    data object WallpaperCrop : Screen(
        route = "wallpaper_crop",
        title = "Crop",
        icon = Icons.Filled.Crop,
        selectedIcon = Icons.Filled.Crop,
    )

    // ── Contact Ringtone Picker ───────────────────────────────────
    data object ContactPicker : Screen(
        route = "contact_picker/{soundId}",
        title = "Pick Contact",
        icon = Icons.Filled.Contacts,
        selectedIcon = Icons.Filled.Contacts,
    ) {
        fun createRoute(soundId: String) = "contact_picker/$soundId"
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
        val bottomNavItems = listOf(Wallpapers, Sounds, Favorites, Settings)
    }
}
