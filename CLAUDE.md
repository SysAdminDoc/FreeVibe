# FreeVibe - CLAUDE.md

## Overview
Open-source Android app for device personalization - wallpapers, ringtones, sounds from multiple free sources.

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
- **v1.5.0** (versionCode 16)
- Version strings in: `app/build.gradle.kts`, `build.gradle.kts` comment, `SettingsScreen.kt` About section, `AppModule.kt` User-Agent, `README.md` badge

## Architecture
```
Compose UI (14 screens, 4 bottom nav tabs)
  ViewModels (Hilt) + SelectedContentHolder singleton
    Wallpaper repos: Wallhaven, Picsum, Bing, Reddit (Wikimedia/NASA disabled - broken APIs)
    Sound repos: Freesound (primary, 600K+ tagged), Internet Archive (secondary, duration-filtered)
    Services: WallpaperApplier, SoundApplier, DownloadManager, AudioTrimmer,
              DualWallpaper, BatchDownload, ContactRingtone, FavoritesExporter,
              OfflineFavorites, WallpaperHistory, VideoWallpaperService
Room DB (v3): favorites, downloads, search_history, wallpaper_cache,
              wallpaper_history, ia_audio_cache
DataStore: Settings, Onboarding
```

## API Keys
- `WALLHAVEN_API_KEY` - Optional, higher rate limits + NSFW
- `FREESOUND_API_KEY` - Required for sound browsing (free key from freesound.org/apiv2/apply/)
- `NASA_API_KEY` - Unused (NASA source disabled)
- Keys stored in `local.properties`, exposed via BuildConfig

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
- Freesound preview URLs (128kbps MP3) don't require OAuth2 — only full-quality downloads do
- SoundRepository gracefully degrades if FREESOUND_API_KEY is empty (IA-only mode)
- Sound tabs use duration-specific queries: Ringtones 3-30s, Notifications 0-8s, Alarms 2-20s
- DurationFilter and SoundCategory enums are in SoundsViewModel.kt, not Models.kt
- SimilarSoundsSection uses rememberCoroutineScope for lazy-load (not LaunchedEffect) to avoid auto-fetching
- AudioTrimmer fade is a lossy byte-level approximation on compressed MP3 — effective but not sample-accurate
- SoundEditorScreen local file picker uses ActivityResultContracts.GetContent("audio/*")
- Wallpaper tabs: WIKIMEDIA and NASA removed from WallpaperTab enum entirely (not just hidden)
- AutoWallpaperWorker still has string-based "wikimedia"/"nasa" branches for backwards compat with saved prefs
- NavHost uses animated transitions (fade+slide) — enterTransition/exitTransition/popEnter/popExit in FreeVibeRoot
- WallpaperDetailScreen uses SubcomposeAsyncImage (not AsyncImage) for loading/error state handling
- SoundsViewModel.loadSimilar() accepts String soundId — dispatches to Freesound API for fs_ prefixed, keyword search for IA
- FavoritesScreen sound list uses SwipeToDismissBox — swipe-to-delete with undo via snackbar + restoreFavorite()
- WallpaperHistoryScreen.onWallpaperClick converts WallpaperHistoryEntity to Wallpaper domain model in FreeVibeRoot
- ActionCircle composable has optional `label` param for accessibility contentDescription

## Key Files
- `FreeVibeApp.kt` - Application class, crash logging, cache eviction on startup
- `FreeVibeRoot.kt` - NavHost with animated transitions, bottom nav, Hilt EntryPoint for SelectedContentHolder
- `AppModule.kt` - Hilt DI module, OkHttp, Retrofit services (incl. FreesoundApi), Room DB with migrations
- `FreesoundApi.kt` - Freesound.org API v2 interface, search/similar endpoints, preview URLs
- `SoundRepository.kt` - Dual-source sound search (Freesound + IA), duration/category filtering, tab-specific queries
- `SoundsViewModel.kt` - Sound browsing, ExoPlayer playback, tabs, 10 categories, duration filters, similar sounds, search history
- `SoundsScreen.kt` - Category chips, duration filter chips, source badges (FS/IA), mini waveform, search history dropdown
- `SoundDetailScreen.kt` - Preview, apply buttons, clickable tags, "More Like This" (all sources)
- `SoundEditorScreen.kt` - Waveform trim, fade in/out sliders, local file picker
- `AudioTrimmer.kt` - Lossless MediaMuxer trim + MP3 fade in/out post-processing
- `WallpapersViewModel.kt` - Wallpaper state, tabs, color search, search history
- `WallpapersScreen.kt` - Search with history dropdown, color picker with clear button
- `WallpaperDetailScreen.kt` - SubcomposeAsyncImage loading/error states, action circles with a11y labels
- `WallpaperCropScreen.kt` - Pinch-zoom crop with aspect ratio presets
- `FavoritesScreen.kt` - Swipe-to-delete sounds, long-press delete wallpapers, undo snackbar
- `SelectedContentHolder.kt` - Singleton state bridge between screens + pendingCategoryQuery
- `WallpaperHistoryScreen.kt` - Browsable grid, tap to re-apply (navigates to detail)
- `SearchHistoryDropdown.kt` - Reusable recent searches dropdown component

## Version History
- v1.5.0: Exposed VideoWallpaperService in Settings (video file picker + live wallpaper launcher), enhanced widget (shuffle counter, last-shuffle timestamp, Favorites quick action, history recording), NSFW purity wired through WallpaperRepository (reads from PreferencesManager), cleaned up AutoWallpaperWorker dead code (removed wikimedia/nasa branches + NasaRepository dep), phone preview live-updating clock (LaunchedEffect 60s ticker)
- v1.4.0: Exposed dual wallpaper split crop in wallpaper detail (DualWallpaperService), batch download all favorites (BatchDownloadService), fixed blur filter in wallpaper editor (downscale-upscale blur), sound auto-preview on detail enter (wired to Settings toggle), NSFW filter toggle in Settings (Wallhaven only, requires API key), auto-load "More Like This" similar sounds
- v1.3.0: Search history wired up (save searches, dropdown with recent queries, delete/clear), aspect ratio presets in crop screen (Free/9:16/16:9/1:1), long-press delete wallpaper favorites with undo, fix deprecated OpenInNew icon
- v1.2.0: Swipe-to-delete favorites with undo snackbar, wallpaper history tap-to-reapply navigation, hide skip on last onboarding page, improved empty states (contextual messages + action buttons), accessibility labels on wallpaper detail action buttons, download history type badges
- v1.1.0: Animated nav transitions (fade+slide), "More Like This" for all sounds (not just Freesound), favorite toggle snackbar feedback, clickable sound tags (search on tap), color filter clear/reset button, cache size display in settings, wallpaper detail loading/error states (SubcomposeAsyncImage), version bump
- v1.0.0: Freesound.org integration (600K+ sounds), dual-source sound search, duration-filtered IA queries, fixed broken trending tab, 10 sound categories, duration filter chips, "More Like This" similar sounds, fade in/out in audio trimmer, local file picker for custom ringtones, removed Wikimedia/NASA wallpaper sources, reordered wallpaper/sound tabs, source badges, tags display, scrollable sound detail
- v0.9.0: Share wallpaper/sound, wallpaper history screen, sound download, phone preview real time, source code link, licenses clickable, sounds shimmer, grid columns setting, editor error handling
- v0.8.0: ContactPicker ringtone wiring, cache eviction, crash logging, confirmation dialogs, category->search, database migrations, version sync
- v0.7.0: Initial full-featured release (7 sources, editors, widget, auto-wallpaper, dual wallpaper, video wallpaper, batch download, offline favorites, export/import)
