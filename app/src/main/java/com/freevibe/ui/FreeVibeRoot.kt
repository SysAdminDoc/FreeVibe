package com.freevibe.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.freevibe.service.SelectedContentHolder
import com.freevibe.ui.navigation.Screen
import com.freevibe.ui.screens.categories.CategoriesScreen
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
import com.freevibe.ui.screens.sounds.ContactPickerScreen
import com.freevibe.ui.screens.sounds.SoundDetailScreen
import com.freevibe.ui.screens.sounds.SoundsScreen
import com.freevibe.ui.screens.wallpapers.WallpaperDetailScreen
import com.freevibe.ui.screens.wallpapers.WallpapersScreen

private const val PREFS_KEY = "freevibe_app"
private const val ONBOARDING_DONE = "onboarding_complete"

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SelectedContentEntryPoint {
    fun selectedContentHolder(): SelectedContentHolder
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeVibeRoot() {
    val context = LocalContext.current
    val selectedContent = remember {
        EntryPointAccessors.fromApplication(context, SelectedContentEntryPoint::class.java)
            .selectedContentHolder()
    }
    val prefs = remember { context.getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE) }
    val onboardingDone = remember { prefs.getBoolean(ONBOARDING_DONE, false) }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = Screen.bottomNavItems.any {
        currentDestination?.hierarchy?.any { dest -> dest.route == it.route } == true
    }

    val startRoute = if (onboardingDone) Screen.Wallpapers.route else Screen.Onboarding.route

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 0.dp,
                ) {
                    Screen.bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == screen.route
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
                                Icon(
                                    imageVector = if (selected) screen.selectedIcon else screen.icon,
                                    contentDescription = screen.title,
                                )
                            },
                            label = { Text(screen.title) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier.padding(padding),
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
            composable(Screen.Wallpapers.route) {
                WallpapersScreen(
                    onWallpaperClick = { wallpaper ->
                        navController.navigate(Screen.WallpaperDetail.createRoute(wallpaper.id))
                    },
                )
            }
            composable(Screen.Sounds.route) {
                SoundsScreen(
                    onSoundClick = { sound ->
                        navController.navigate(Screen.SoundDetail.createRoute(sound.id))
                    },
                )
            }
            composable(Screen.Favorites.route) {
                FavoritesScreen(
                    onWallpaperClick = { fav ->
                        navController.navigate(Screen.WallpaperDetail.createRoute(fav.id))
                    },
                    onSoundClick = { fav ->
                        navController.navigate(Screen.SoundDetail.createRoute(fav.id))
                    },
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onDownloadsClick = { navController.navigate(Screen.Downloads.route) },
                    onLicensesClick = { navController.navigate(Screen.Licenses.route) },
                    onCategoriesClick = { navController.navigate(Screen.Categories.route) },
                )
            }

            // ── Detail screens ────────────────────────────────────
            composable(Screen.WallpaperDetail.route) {
                WallpaperDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Screen.WallpaperEditor.route) },
                    onCrop = { navController.navigate(Screen.WallpaperCrop.route) },
                )
            }
            composable(Screen.SoundDetail.route) {
                SoundDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Screen.SoundEditor.route) },
                    onContactPicker = { soundId ->
                        navController.navigate(Screen.ContactPicker.createRoute(soundId))
                    },
                )
            }

            // ── Editors ───────────────────────────────────────────
            composable(Screen.WallpaperEditor.route) {
                WallpaperEditorScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.WallpaperCrop.route) {
                WallpaperCropScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.SoundEditor.route) {
                SoundEditorScreen(onBack = { navController.popBackStack() })
            }

            // ── Contact Picker ────────────────────────────────────
            composable(Screen.ContactPicker.route) {
                ContactPickerScreen(onBack = { navController.popBackStack() })
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
                        selectedContent.pendingCategoryQuery = query
                        navController.navigate(Screen.Wallpapers.route) {
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
        }
    }
}
