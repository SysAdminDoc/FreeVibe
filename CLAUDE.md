# FreeVibe - CLAUDE.md

## Overview
Open-source Android app for device personalization - wallpapers, ringtones, sounds from 7 free sources. No API keys required.

## Tech Stack
- Kotlin 2.1.0 / Jetpack Compose / Material 3
- Hilt 2.53.1 DI, Room 2.6.1 DB, Retrofit 2.11.0 + OkHttp, Moshi + KSP
- Coil 2.7.0 (images), Media3 ExoPlayer (audio), WorkManager 2.10.0, Glance 1.1.1 (widget)
- Min SDK 26, Target SDK 35, JDK 17

## Build
```bash
./gradlew assembleDebug      # gradlew.bat on Windows
./gradlew assembleRelease     # requires signing config
```
Gradle 8.12 pinned via wrapper. AGP 8.7.3.

## Version
- **v0.8.0** (versionCode 9)
- Version strings in: `app/build.gradle.kts`, `build.gradle.kts` comment, `SettingsScreen.kt` About section, `AppModule.kt` User-Agent, `README.md` badge

## Architecture
```
Compose UI (13 screens, 4 bottom nav tabs)
  ViewModels (Hilt) + SelectedContentHolder singleton
    Repositories: Wallhaven, Picsum, Bing, Wikimedia, IA, Reddit, NASA
    Services: WallpaperApplier, SoundApplier, DownloadManager, AudioTrimmer,
              DualWallpaper, BatchDownload, ContactRingtone, FavoritesExporter,
              OfflineFavorites, WallpaperHistory, VideoWallpaperService
Room DB (v3): favorites, downloads, search_history, wallpaper_cache,
              wallpaper_history, ia_audio_cache
DataStore: Settings, Onboarding
```

## Key Files
- `FreeVibeApp.kt` - Application class, crash logging, cache eviction on startup
- `FreeVibeRoot.kt` - NavHost with all routes, bottom nav, Hilt EntryPoint for SelectedContentHolder
- `AppModule.kt` - Hilt DI module, OkHttp, Retrofit services, Room DB with migrations
- `SelectedContentHolder.kt` - Singleton state bridge between screens + pendingCategoryQuery
- `WallpapersViewModel.kt` - Main wallpaper state, search, category query consumption
- `ContactPickerScreen.kt` - Downloads sound + sets as contact ringtone via SoundApplier

## Database Migrations
- v1->2: Added wallpaper_cache + wallpaper_history tables
- v2->3: Added ia_audio_cache table
- Migrations defined in AppModule.kt. Uses fallbackToDestructiveMigrationOnDowngrade() only.

## Gotchas
- `SelectedContentHolder.pendingCategoryQuery` is consumed on WallpapersViewModel init (set to null after read)
- Category navigation uses `saveState=false, restoreState=false` to force ViewModel recreation
- FreeVibeRoot uses Hilt EntryPoint (not ViewModel) to access SelectedContentHolder from Composable
- Unicode box-drawing chars in section comments - use caution with string matching
- Crash log at `filesDir/crash.log`, auto-trimmed to 500KB

## Version History
- v0.8.0: ContactPicker ringtone wiring, cache eviction scheduling, crash logging, confirmation dialogs, category->search flow, database migrations, version sync
- v0.7.0: Initial full-featured release (7 sources, editors, widget, auto-wallpaper, dual wallpaper, video wallpaper, batch download, offline favorites, export/import)
