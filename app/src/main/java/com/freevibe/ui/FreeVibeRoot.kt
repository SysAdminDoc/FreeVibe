package com.freevibe.ui

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Sound
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.remote.toSound
import com.freevibe.data.remote.toWallpaper
import com.freevibe.ui.navigation.Screen
import com.freevibe.ui.screens.categories.CategoriesScreen
import com.freevibe.ui.screens.collections.CollectionsScreen
import com.freevibe.ui.screens.videowallpapers.VideoWallpapersScreen
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.freevibe.ui.screens.downloads.DownloadsScreen
import com.freevibe.ui.screens.editor.SoundEditorScreen
import com.freevibe.ui.screens.editor.WallpaperCropScreen
import com.freevibe.ui.screens.editor.WallpaperEditorScreen
import com.freevibe.ui.screens.favorites.FavoritesScreen
import com.freevibe.ui.screens.licenses.LicensesScreen
import com.freevibe.ui.screens.onboarding.OnboardingScreen
import com.freevibe.ui.screens.settings.SettingsScreen
import com.freevibe.ui.screens.settings.WallpaperHistoryScreen
import com.freevibe.ui.screens.sounds.ContactPickerScreen
import com.freevibe.ui.screens.sounds.SoundDetailScreen
import com.freevibe.ui.screens.sounds.SoundsScreen
import com.freevibe.ui.screens.wallpapers.WallpaperDetailScreen
import com.freevibe.ui.screens.wallpapers.WallpapersScreen

private const val PREFS_KEY = "freevibe_app"
private const val ONBOARDING_DONE = "onboarding_complete"

@EntryPoint
@InstallIn(SingletonComponent::class)
interface FreeVibeRootEntryPoint {
    fun favoritesRepository(): com.freevibe.data.repository.FavoritesRepository
    fun applyFeedbackBus(): com.freevibe.service.ApplyFeedbackBus
    fun wallpaperApplier(): com.freevibe.service.WallpaperApplier
    fun wallpaperHistoryManager(): com.freevibe.service.WallpaperHistoryManager
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeVibeRoot(
    initialNavigateTo: String? = null,
    initialWallpaper: Wallpaper? = null,
    navigationToken: Long = 0L,
) {
    val context = LocalContext.current
    val entryPoint = remember {
        EntryPointAccessors.fromApplication(context, FreeVibeRootEntryPoint::class.java)
    }
    val favoritesCount by remember { entryPoint.favoritesRepository().count() }.collectAsStateWithLifecycle(initialValue = 0)
    val prefs = remember { context.getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE) }
    val onboardingDone = remember { prefs.getBoolean(ONBOARDING_DONE, false) }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Handle deep-link navigation from widget or notification
    LaunchedEffect(navigationToken, initialNavigateTo, initialWallpaper?.id) {
        val route = when {
            initialWallpaper != null -> Screen.WallpaperDetail.createRoute(initialWallpaper)
            initialNavigateTo == "favorites" -> Screen.Favorites.route
            else -> initialNavigateTo
        }

        if (route != null) {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = route == Screen.Favorites.route
                }
                launchSingleTop = true
                restoreState = route == Screen.Favorites.route
            }
        }
    }

    val showBottomBar = Screen.bottomNavItems.any {
        currentDestination?.hierarchy?.any { dest -> it.matchesDestination(dest.route) } == true
    }

    val startRoute = if (onboardingDone) Screen.Wallpapers.route else Screen.Onboarding.route

    // Global "Applied — Undo" snackbar host. Any ViewModel that applies a wallpaper posts
    // to ApplyFeedbackBus; we observe it here at the root so the snackbar persists across
    // navigation (e.g. tap Apply on detail, back to list, snackbar still visible).
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        entryPoint.applyFeedbackBus().events.collect { event ->
            val result = snackbarHostState.showSnackbar(
                message = event.message,
                actionLabel = if (event.undoTarget != null) "Undo" else null,
                duration = SnackbarDuration.Short,
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed && event.undoTarget != null) {
                val entry = event.undoTarget
                scope.launch {
                    val target = runCatching {
                        com.freevibe.data.model.WallpaperTarget.valueOf(entry.target)
                    }.getOrDefault(com.freevibe.data.model.WallpaperTarget.BOTH)
                    entryPoint.wallpaperApplier().applyFromUrl(entry.fullUrl, target)
                        .onSuccess {
                            entryPoint.applyFeedbackBus().post(
                                com.freevibe.service.ApplyFeedbackEvent(
                                    message = "Reverted to previous wallpaper",
                                    undoTarget = null,
                                )
                            )
                        }
                        .onFailure { e ->
                            entryPoint.applyFeedbackBus().post(
                                com.freevibe.service.ApplyFeedbackEvent(
                                    message = "Undo failed: ${e.message ?: "unknown error"}",
                                    undoTarget = null,
                                )
                            )
                        }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .size(280.dp)
                .offset(x = (-40).dp, y = (-72).dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .size(340.dp)
                .offset(x = 180.dp, y = 420.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = {
                // Lift the snackbar above the bottom nav bar when it's visible so it doesn't
                // sit behind the floating nav pill.
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(bottom = if (showBottomBar) 72.dp else 0.dp),
                )
            },
            bottomBar = {
                if (showBottomBar) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(
                                androidx.compose.foundation.layout.WindowInsets.navigationBars.only(
                                    androidx.compose.foundation.layout.WindowInsetsSides.Bottom,
                                ),
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            tonalElevation = 0.dp,
                            shadowElevation = 10.dp,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            ),
                        ) {
                            NavigationBar(
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                tonalElevation = 0.dp,
                                windowInsets = WindowInsets(0, 0, 0, 0),
                                modifier = Modifier
                                    .height(60.dp)
                                    .padding(horizontal = 2.dp),
                            ) {
                                Screen.bottomNavItems.forEach { screen ->
                                    val selected = currentDestination?.hierarchy?.any {
                                        screen.matchesDestination(it.route)
                                    } == true

                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = {
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = {
                                            if (screen == Screen.Favorites && favoritesCount > 0) {
                                                BadgedBox(badge = {
                                                    Badge(
                                                        containerColor = MaterialTheme.colorScheme.primary,
                                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                                    ) {
                                                        Text(if (favoritesCount > 99) "99+" else "$favoritesCount")
                                                    }
                                                }) {
                                                    Icon(
                                                        imageVector = if (selected) screen.selectedIcon else screen.icon,
                                                        contentDescription = screen.title,
                                                    )
                                                }
                                            } else {
                                                Icon(
                                                    imageVector = if (selected) screen.selectedIcon else screen.icon,
                                                    contentDescription = screen.title,
                                                )
                                            }
                                        },
                                        label = {
                                            Text(
                                                screen.title,
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                            )
                                        },
                                        alwaysShowLabel = false,
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = startRoute,
                modifier = Modifier.padding(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding(),
                ),
                enterTransition = { fadeIn(tween(250, easing = androidx.compose.animation.core.FastOutSlowInEasing)) + slideInHorizontally(tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it / 5 } },
                exitTransition = { fadeOut(tween(200, easing = androidx.compose.animation.core.FastOutLinearInEasing)) },
                popEnterTransition = { fadeIn(tween(250, easing = androidx.compose.animation.core.FastOutSlowInEasing)) + slideInHorizontally(tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { -it / 5 } },
                popExitTransition = { fadeOut(tween(200, easing = androidx.compose.animation.core.FastOutLinearInEasing)) + slideOutHorizontally(tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it / 5 } },
            ) {
            // ── Onboarding ────────────────────────────────────────
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onComplete = {
                        prefs.edit().putBoolean(ONBOARDING_DONE, true).apply()
                        navController.navigate(Screen.Wallpapers.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    },
                )
            }

            // ── Main tabs ─────────────────────────────────────────
            composable(
                route = Screen.Wallpapers.destinationPattern,
                arguments = listOf(
                    navArgument("query") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("color") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("similarId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("similarSource") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("similarFullUrl") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { backStackEntry ->
                WallpapersScreen(
                    initialQuery = backStackEntry.arguments?.getString("query")?.ifBlank { null },
                    initialColor = backStackEntry.arguments?.getString("color")?.ifBlank { null },
                    initialSimilarId = backStackEntry.arguments?.getString("similarId")?.ifBlank { null },
                    initialSimilarSource = backStackEntry.arguments?.getString("similarSource")?.ifBlank { null },
                    initialSimilarFullUrl = backStackEntry.arguments?.getString("similarFullUrl")?.ifBlank { null },
                    onWallpaperClick = { wallpaper ->
                        navController.navigate(Screen.WallpaperDetail.createRoute(wallpaper)) { launchSingleTop = true }
                    },
                )
            }
            composable(Screen.VideoWallpapers.route) {
                VideoWallpapersScreen(
                    onPreview = { streamUrl, title ->
                        navController.navigate(
                            Screen.VideoWallpaperPreview.createRoute(streamUrl, title)
                        ) { launchSingleTop = true }
                    },
                )
            }
            composable(
                route = Screen.Sounds.destinationPattern,
                arguments = listOf(
                    navArgument("query") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { backStackEntry ->
                SoundsScreen(
                    onSoundClick = { sound ->
                        navController.navigate(Screen.SoundDetail.createRoute(sound)) { launchSingleTop = true }
                    },
                    onCreateRingtone = {
                        navController.navigate(Screen.SoundEditor.createRoute()) { launchSingleTop = true }
                    },
                    initialQuery = backStackEntry.arguments?.getString("query")?.ifBlank { null },
                )
            }
            composable(Screen.Favorites.route) {
                FavoritesScreen(
                    onWallpaperClick = { fav ->
                        navController.navigate(Screen.WallpaperDetail.createRoute(fav.toWallpaper())) { launchSingleTop = true }
                    },
                    onSoundClick = { fav ->
                        navController.navigate(Screen.SoundDetail.createRoute(fav.toSound())) { launchSingleTop = true }
                    },
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onDownloadsClick = { navController.navigate(Screen.Downloads.route) { launchSingleTop = true } },
                    onLicensesClick = { navController.navigate(Screen.Licenses.route) { launchSingleTop = true } },
                    onCategoriesClick = { navController.navigate(Screen.Categories.route) { launchSingleTop = true } },
                    onHistoryClick = { navController.navigate(Screen.WallpaperHistory.route) { launchSingleTop = true } },
                    onCollectionsClick = { navController.navigate(Screen.Collections.route) { launchSingleTop = true } },
                )
            }

            // ── Detail screens ────────────────────────────────────
            composable(
                Screen.WallpaperDetail.destinationPattern,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("source") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("thumbnailUrl") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("fullUrl") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("width") {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                    navArgument("height") {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                )
            ) { backStackEntry ->
                val wallpaperId = backStackEntry.arguments?.getString("id").orEmpty()
                val fullUrl = backStackEntry.arguments?.getString("fullUrl").orEmpty()
                val fallbackWallpaper = fullUrl.takeIf { it.isNotBlank() }?.let {
                    Wallpaper(
                        id = wallpaperId,
                        source = backStackEntry.arguments?.getString("source")
                            ?.let { sourceName -> runCatching { ContentSource.valueOf(sourceName) }.getOrDefault(ContentSource.WALLHAVEN) }
                            ?: ContentSource.WALLHAVEN,
                        thumbnailUrl = backStackEntry.arguments?.getString("thumbnailUrl").orEmpty().ifBlank { fullUrl },
                        fullUrl = fullUrl,
                        width = backStackEntry.arguments?.getInt("width") ?: 0,
                        height = backStackEntry.arguments?.getInt("height") ?: 0,
                    )
                }
                WallpaperDetailScreen(
                    wallpaperId = wallpaperId,
                    fallbackWallpaper = fallbackWallpaper,
                    onBack = { navController.popBackStack() },
                    onEdit = { wallpaper -> navController.navigate(Screen.WallpaperEditor.createRoute(wallpaper)) { launchSingleTop = true } },
                    onCrop = { wallpaper -> navController.navigate(Screen.WallpaperCrop.createRoute(wallpaper)) { launchSingleTop = true } },
                    onPreview = { wallpaper -> navController.navigate(Screen.WallpaperPreview.createRoute(wallpaper)) { launchSingleTop = true } },
                    onSearchTag = { tag ->
                        navController.navigate(Screen.Wallpapers.createRoute(query = tag)) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = false
                            }
                            launchSingleTop = true
                            restoreState = false
                        }
                    },
                    onSearchColor = { colorHex ->
                        navController.navigate(Screen.Wallpapers.createRoute(color = colorHex)) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = false
                            }
                            launchSingleTop = true
                            restoreState = false
                        }
                    },
                    onFindSimilar = { wallpaper ->
                        navController.navigate(Screen.Wallpapers.createSimilarRoute(wallpaper)) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = false
                            }
                            launchSingleTop = true
                            restoreState = false
                        }
                    },
                )
            }
            composable(
                Screen.SoundDetail.destinationPattern,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("source") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("name") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("previewUrl") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("downloadUrl") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                )
            ) { backStackEntry ->
                val soundId = backStackEntry.arguments?.getString("id").orEmpty()
                val fallbackSound = backStackEntry.arguments?.getString("name")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { name ->
                        Sound(
                            id = soundId,
                            source = backStackEntry.arguments?.getString("source")
                                ?.let { sourceName ->
                                    runCatching { ContentSource.valueOf(sourceName) }
                                        .getOrDefault(ContentSource.LOCAL)
                                }
                                ?: ContentSource.LOCAL,
                            name = name,
                            previewUrl = backStackEntry.arguments?.getString("previewUrl").orEmpty(),
                            downloadUrl = backStackEntry.arguments?.getString("downloadUrl").orEmpty(),
                        )
                    }
                SoundDetailScreen(
                    soundId = soundId,
                    fallbackSound = fallbackSound,
                    onBack = { navController.popBackStack() },
                    onEdit = { sound -> navController.navigate(Screen.SoundEditor.createRoute(sound)) { launchSingleTop = true } },
                    onContactPicker = { sound ->
                        navController.navigate(Screen.ContactPicker.createRoute(sound)) { launchSingleTop = true }
                    },
                    onOpenSound = { sound ->
                        navController.navigate(Screen.SoundDetail.createRoute(sound)) { launchSingleTop = true }
                    },
                    onSearchTag = { tag ->
                        navController.navigate(Screen.Sounds.createRoute(query = tag)) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = false
                            }
                            launchSingleTop = true
                            restoreState = false
                        }
                    },
                )
            }

            // ── Editors ───────────────────────────────────────────
            composable(
                Screen.WallpaperEditor.destinationPattern,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("source") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("thumbnailUrl") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("fullUrl") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("width") {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                    navArgument("height") {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                )
            ) { backStackEntry ->
                val wallpaperId = backStackEntry.arguments?.getString("id").orEmpty()
                val fullUrl = backStackEntry.arguments?.getString("fullUrl").orEmpty()
                val fallbackWallpaper = fullUrl.takeIf { it.isNotBlank() }?.let {
                    Wallpaper(
                        id = wallpaperId,
                        source = backStackEntry.arguments?.getString("source")
                            ?.let { sourceName -> runCatching { ContentSource.valueOf(sourceName) }.getOrDefault(ContentSource.WALLHAVEN) }
                            ?: ContentSource.WALLHAVEN,
                        thumbnailUrl = backStackEntry.arguments?.getString("thumbnailUrl").orEmpty().ifBlank { fullUrl },
                        fullUrl = fullUrl,
                        width = backStackEntry.arguments?.getInt("width") ?: 0,
                        height = backStackEntry.arguments?.getInt("height") ?: 0,
                    )
                }
                WallpaperEditorScreen(
                    wallpaperId = wallpaperId,
                    fallbackWallpaper = fallbackWallpaper,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Screen.VideoWallpaperPreview.destinationPattern,
                arguments = listOf(
                    navArgument("streamUrl") { type = NavType.StringType },
                    navArgument("title") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = ""
                    },
                ),
            ) { backStackEntry ->
                val streamUrl = backStackEntry.arguments?.getString("streamUrl").orEmpty()
                val title = backStackEntry.arguments?.getString("title").orEmpty()
                com.freevibe.ui.screens.videowallpapers.VideoWallpaperPreviewScreen(
                    streamUrl = streamUrl,
                    title = title,
                    onBack = { navController.popBackStack() },
                    onApply = {
                        // Pop back to the source (video wallpapers list / detail) so the
                        // Apply flow that lives there fires. User already had the stream
                        // context before entering preview.
                        navController.popBackStack()
                    },
                    onCrop = { navController.popBackStack() },
                )
            }
            composable(
                Screen.WallpaperPreview.destinationPattern,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("source") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("thumbnailUrl") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("fullUrl") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("width") {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                    navArgument("height") {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                )
            ) { backStackEntry ->
                val wallpaperId = backStackEntry.arguments?.getString("id").orEmpty()
                val fullUrl = backStackEntry.arguments?.getString("fullUrl").orEmpty()
                val wallpaper = Wallpaper(
                    id = wallpaperId,
                    source = backStackEntry.arguments?.getString("source")
                        ?.let { sourceName -> runCatching { ContentSource.valueOf(sourceName) }.getOrDefault(ContentSource.WALLHAVEN) }
                        ?: ContentSource.WALLHAVEN,
                    thumbnailUrl = backStackEntry.arguments?.getString("thumbnailUrl").orEmpty().ifBlank { fullUrl },
                    fullUrl = fullUrl,
                    width = backStackEntry.arguments?.getInt("width") ?: 0,
                    height = backStackEntry.arguments?.getInt("height") ?: 0,
                )
                val previewVm: com.freevibe.ui.screens.wallpapers.WallpapersViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                com.freevibe.ui.screens.wallpapers.WallpaperPreviewScreen(
                    wallpaper = wallpaper,
                    onBack = { navController.popBackStack() },
                    onApply = { target ->
                        previewVm.applyWallpaper(wallpaper, target)
                        navController.popBackStack()
                    },
                    viewModel = previewVm,
                )
            }
            composable(
                Screen.WallpaperCrop.destinationPattern,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("source") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("thumbnailUrl") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("fullUrl") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("width") {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                    navArgument("height") {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                )
            ) { backStackEntry ->
                val wallpaperId = backStackEntry.arguments?.getString("id").orEmpty()
                val fullUrl = backStackEntry.arguments?.getString("fullUrl").orEmpty()
                val fallbackWallpaper = fullUrl.takeIf { it.isNotBlank() }?.let {
                    Wallpaper(
                        id = wallpaperId,
                        source = backStackEntry.arguments?.getString("source")
                            ?.let { sourceName -> runCatching { ContentSource.valueOf(sourceName) }.getOrDefault(ContentSource.WALLHAVEN) }
                            ?: ContentSource.WALLHAVEN,
                        thumbnailUrl = backStackEntry.arguments?.getString("thumbnailUrl").orEmpty().ifBlank { fullUrl },
                        fullUrl = fullUrl,
                        width = backStackEntry.arguments?.getInt("width") ?: 0,
                        height = backStackEntry.arguments?.getInt("height") ?: 0,
                    )
                }
                WallpaperCropScreen(
                    wallpaperId = wallpaperId,
                    fallbackWallpaper = fallbackWallpaper,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Screen.SoundEditor.destinationPattern,
                arguments = listOf(
                    navArgument("soundId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = ""
                    },
                    navArgument("source") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("name") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("previewUrl") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("downloadUrl") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val soundId = backStackEntry.arguments?.getString("soundId").orEmpty().ifBlank { null }
                val fallbackSound = soundId?.let { id ->
                    backStackEntry.arguments?.getString("name")
                        ?.takeIf { it.isNotBlank() }
                        ?.let { name ->
                            Sound(
                                id = id,
                                source = backStackEntry.arguments?.getString("source")
                                    ?.let { sourceName ->
                                        runCatching { ContentSource.valueOf(sourceName) }
                                            .getOrDefault(ContentSource.LOCAL)
                                    }
                                    ?: ContentSource.LOCAL,
                                name = name,
                                previewUrl = backStackEntry.arguments?.getString("previewUrl").orEmpty(),
                                downloadUrl = backStackEntry.arguments?.getString("downloadUrl").orEmpty(),
                            )
                        }
                }
                SoundEditorScreen(
                    soundId = soundId,
                    fallbackSound = fallbackSound,
                    onBack = { navController.popBackStack() },
                )
            }

            // ── Contact Picker ────────────────────────────────────
            composable(
                Screen.ContactPicker.destinationPattern,
                arguments = listOf(
                    navArgument("soundId") { type = NavType.StringType },
                    navArgument("source") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("name") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("previewUrl") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("downloadUrl") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                )
            ) { backStackEntry ->
                val soundId = backStackEntry.arguments?.getString("soundId").orEmpty()
                val fallbackSound = backStackEntry.arguments?.getString("name")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { name ->
                        com.freevibe.data.model.Sound(
                            id = soundId,
                            source = backStackEntry.arguments?.getString("source")
                                ?.let { sourceName ->
                                    runCatching { com.freevibe.data.model.ContentSource.valueOf(sourceName) }
                                        .getOrDefault(com.freevibe.data.model.ContentSource.LOCAL)
                                }
                                ?: com.freevibe.data.model.ContentSource.LOCAL,
                            name = name,
                            previewUrl = backStackEntry.arguments?.getString("previewUrl").orEmpty(),
                            downloadUrl = backStackEntry.arguments?.getString("downloadUrl").orEmpty(),
                        )
                    }
                ContactPickerScreen(
                    soundId = soundId,
                    fallbackSound = fallbackSound,
                    onBack = { navController.popBackStack() },
                )
            }

            // ── Downloads ─────────────────────────────────────────
            composable(Screen.Downloads.route) {
                DownloadsScreen(onBack = { navController.popBackStack() })
            }

            // ── Categories ────────────────────────────────────────
            composable(Screen.Categories.route) {
                CategoriesScreen(
                    onBack = { navController.popBackStack() },
                    onCategoryClick = { query ->
                        navController.navigate(Screen.Wallpapers.createRoute(query = query)) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = false
                            }
                            launchSingleTop = true
                            restoreState = false
                        }
                    },
                )
            }

            // ── Licenses ──────────────────────────────────────────
            composable(Screen.Licenses.route) {
                LicensesScreen(onBack = { navController.popBackStack() })
            }

            // ── Collections ────────────────────────────────────
            composable(Screen.Collections.route) {
                CollectionsScreen(
                    onBack = { navController.popBackStack() },
                    onWallpaperClick = { wallpaper ->
                        navController.navigate(Screen.WallpaperDetail.createRoute(wallpaper)) { launchSingleTop = true }
                    },
                )
            }

            // ── Wallpaper History ────────────────────────────────
            composable(Screen.WallpaperHistory.route) {
                WallpaperHistoryScreen(
                    onBack = { navController.popBackStack() },
                    onWallpaperClick = { entry ->
                        val wallpaper = com.freevibe.data.model.Wallpaper(
                            id = entry.wallpaperId,
                            source = try { com.freevibe.data.model.ContentSource.valueOf(entry.source) }
                                catch (_: Exception) { com.freevibe.data.model.ContentSource.WALLHAVEN },
                            thumbnailUrl = entry.thumbnailUrl,
                            fullUrl = entry.fullUrl,
                            width = entry.width,
                            height = entry.height,
                        )
                        navController.navigate(Screen.WallpaperDetail.createRoute(wallpaper)) { launchSingleTop = true }
                    },
                )
            }
            }
        }
    }
}
