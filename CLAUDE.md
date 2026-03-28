# Aura - CLAUDE.md

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
- **v3.0.0** (versionCode 29)
- Version strings in: `app/build.gradle.kts`, `SettingsScreen.kt` About section, `AppModule.kt` User-Agent, `README.md` badge

## Architecture
```
Compose UI (16 screens, 5 bottom nav tabs)
  ViewModels (Hilt) + SelectedContentHolder singleton
    Wallpaper repos: Wallhaven, Picsum, Bing, Reddit
    Sound repos: Freesound (primary, 600K+ tagged), Internet Archive (secondary, duration-filtered)
    Services: WallpaperApplier, SoundApplier, DownloadManager, AudioTrimmer,
              DualWallpaper, BatchDownload, ContactRingtone, FavoritesExporter,
              OfflineFavorites, WallpaperHistory, VideoWallpaperService, CollectionRepository
Room DB (v4): favorites, downloads, search_history, wallpaper_cache,
              wallpaper_history, ia_audio_cache, wallpaper_collections, wallpaper_collection_items
DataStore: Settings, Onboarding
```

## API Keys
- `WALLHAVEN_API_KEY` - Optional, higher rate limits + NSFW
- `FREESOUND_API_KEY` - Required for sound browsing (free key from freesound.org/apiv2/apply/)
- Keys stored in `local.properties`, exposed via BuildConfig

## Database Migrations
- v1->2: Added wallpaper_cache + wallpaper_history tables
- v2->3: Added ia_audio_cache table
- v3->4: Added wallpaper_collections + wallpaper_collection_items tables
- Migrations defined in AppModule.kt. Uses fallbackToDestructiveMigrationOnDowngrade() only.

## Gotchas
- `SelectedContentHolder.pendingCategoryQuery` is consumed on WallpapersViewModel init (set to null after read)
- Category navigation uses `saveState=false, restoreState=false` to force ViewModel recreation
- AuraRoot uses Hilt EntryPoint (not ViewModel) to access SelectedContentHolder from Composable
- Unicode box-drawing chars in section comments - use caution with string matching
- Crash log at `filesDir/crash.log`, auto-trimmed to 500KB
- Freesound preview URLs (128kbps MP3) don't require OAuth2 — only full-quality downloads do
- SoundRepository gracefully degrades if FREESOUND_API_KEY is empty (IA-only mode)
- Sound tabs use duration-specific queries: Ringtones 3-30s, Notifications 0-8s, Alarms 2-20s
- DurationFilter and SoundCategory enums are in SoundsViewModel.kt, not Models.kt
- SimilarSoundsSection uses rememberCoroutineScope for lazy-load (not LaunchedEffect) to avoid auto-fetching
- AudioTrimmer fade is a lossy byte-level approximation on compressed MP3 — effective but not sample-accurate
- SoundEditorScreen local file picker uses ActivityResultContracts.GetContent("audio/*")
- NASA/Wikimedia API files fully deleted in v2.1.0 — enum values kept in ContentSource for legacy favorites compatibility
- WallpaperRepository no longer depends on WikimediaApi (constructor param removed)
- NavHost uses animated transitions (fade+slide) — enterTransition/exitTransition/popEnter/popExit in AuraRoot
- WallpaperDetailScreen uses SubcomposeAsyncImage (not AsyncImage) for loading/error state handling
- SoundsViewModel.loadSimilar() accepts String soundId — dispatches to Freesound API for fs_ prefixed, keyword search for IA
- FavoritesScreen sound list uses SwipeToDismissBox — swipe-to-delete with undo via snackbar + restoreFavorite()
- WallpaperHistoryScreen.onWallpaperClick converts WallpaperHistoryEntity to Wallpaper domain model in AuraRoot
- ActionCircle composable has optional `label` param for accessibility contentDescription

## Key Files
- `AuraApp.kt` - Application class, crash logging, cache eviction on startup
- `AuraRoot.kt` - NavHost with animated transitions, bottom nav, Hilt EntryPoint for SelectedContentHolder
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
- `CollectionsScreen.kt` - Wallpaper collections list + detail grid, CollectionsViewModel
- `CollectionRepository.kt` - CRUD for wallpaper collections (Room-backed)

## Version History
- v3.0.0: YouTube integration (NewPipe Extractor search + yt-dlp stream extraction). Video Wallpapers tab with ExoPlayer auto-playing cards, crop editor for landscape-to-portrait conversion, yt-dlp FFmpeg crop. VideoWallpaperService: file timestamp hot-swap, looping. 5 bottom nav tabs. Wallpaper detail redesign (prominent Apply button). Search crash fix (ScrollableTabRow key). Sound duration limits (research-based: ringtones 5-30s, notifications 0-3s, alarms 5-40s). Progressive sound loading with throttled UI updates. YouTube result junk filtering. Pre-resolution of audio URLs with caching. FileProvider for video sharing. Samsung live wallpaper picker support.
- v2.7.0: Sound loading progress indicator ("Fetching sounds... 12/50" bar during IA metadata resolution). Remove Trending tab (default to Ringtones). Fix duration limits: notifications max 5s, ringtones/alarms max 4min. Duration filter chips updated (All/< 5s/5-30s/30s-4m). Remove dead getTrending() from SoundRepository.
- v2.6.0: Delete dead FreesoundApi.kt + FreesoundSound.toSound() mapper. Sync all version strings to v2.6.0 (README badge, root build.gradle.kts comment, Settings, User-Agent). Update README feature table with v2.1-2.5 additions (collections, shuffle FAB, parallax, haptic, auto-wallpaper from favorites, editor presets/undo, HTTP validation). Update README architecture (15 screens, Room v4, Collections). Clean stale Freesound comment from AppModule.
- v2.5.0: Validate HTTP response codes across all network callers — SoundApplier, OfflineFavoritesManager, DownloadManager, WallpaperEditorVM, SoundEditorVM, WallpaperCropVM now check response.isSuccessful before processing body (prevents writing error pages as media). SoundRepository fetch rows increased from 30 to 50 for better fill rate after duration filtering.
- v2.4.0: Fix VideoWallpaperService crash on missing video file (existence check before setDataSource). Fix WallpaperCacheManager valueOf crash on unknown source (try-catch fallback). Remove dead onSearchTag callback from AuraRoot. Collections: long-press to remove wallpapers from collection (snackbar feedback). Clean stale NASA/Wikimedia TTL constants from WallpaperCacheManager. Remove unused imports from FavoritesScreen.
- v2.3.0: Fix WallpaperCropScreen aspect ratio preset math (proper viewport-relative scaling). Add "Favorites" as auto-wallpaper source (AutoWallpaperWorker + SettingsScreen picker). Fix FavoritesExporter import crash on malformed JSON (catch + user-friendly error). Haptic feedback on wallpaper favorite toggle. Updated onboarding features page (Collections + Auto-Wallpaper replace Dark Theme + Offline Ready). Fix DownloadsScreen silent catch on file open failure (snackbar). Random wallpaper shuffle FAB on WallpapersScreen.
- v2.2.0: Fix AudioTrimmer resource leak (try-finally for extractor/muxer) + unsigned byte fade. Fix SoundEditor extractWaveform resource leak + playback race condition (state-based loop instead of player-based). Fix DualWallpaperService bitmap leak on exception (try-finally recycle). Batch WallpaperEditor preset application (single applyPreset() instead of 4 separate filter calls). Sound editor undo (one-level snapshot of trim/fade state, Undo button in top bar). Widget shuffle error feedback (Toast on failure instead of silent catch).
- v2.1.0: Remove dead NASA/Wikimedia code (files, AppModule providers, WallpaperRepository dep, mappers). Fix unsafe Activity cast in Theme.kt. Batch favorite status loading (single query for all IDs instead of N per-card Flow collectors). Parallax effect on wallpaper detail pager (scale + translate + alpha on page offset). Wallpaper collections feature: Room entities + migration v3->v4, CollectionDao, CollectionRepository, CollectionsScreen (list + detail grid), "Save to Collection" bottom sheet on wallpaper detail, Settings entry.
- v2.0.0: Animated favorite heart overlay on wallpaper grid cards (tap to toggle, spring bounce animation), sound detail waveform visualization (60-bar Canvas with animated playback progress), onboarding feature stagger animations (slide-in from left, 80ms delay per row), glassmorphic bottom sheets (92% alpha, 12dp tonal elevation), download progress shows percentage, fix deprecated VolumeUp icon
- v1.9.0: Per-card shimmer loading placeholders in wallpaper grid (SubcomposeAsyncImage), long-press to favorite from grid, editor preset filters (Warm/Cool/Vivid/Cinematic/Dreamy/B&W), staggered category card entrance animations, favorites sorting (Recent/Name/Oldest), fix Reddit pagination (reset afterToken on all tab switches)
- v1.8.0: Rewrote SoundRepository — targets curated IA collections (freesound, opensource_audio, sound_effects) instead of general search, excludes podcasts/radio/spoken word, semaphore-limited concurrent metadata fetches (max 5), 8s timeout per fetch, fetches 30 items to compensate for duration-filtered rejects. Vertical pager on wallpaper detail screen for swipe up/down between wallpapers with auto-load-more.
- v1.7.0: Bug audit & fixes — MediaPlayer try-catch in sound editor playback loop, cursor index bounds check in ContactRingtoneService, path-based MIME detection in DownloadManager, fade slider min range guard, DualWallpaperService cropHeight bounds protection, BatchDownloadService try-finally count tracking, OOM protection in wallpaper editor bitmap creation
- v1.6.0: Wired 3 hidden preferences (sound preview volume slider, Reddit subreddits editor dialog with quick-add chips, resolution preference picker with FHD/QHD/4K), bottom nav favorites badge (BadgedBox with count), Wallhaven views/favorites metadata on detail screen, resolution filter wired to Wallhaven API (atleast parameter), preview volume applied to ExoPlayer playback
- v1.5.0: Exposed VideoWallpaperService in Settings (video file picker + live wallpaper launcher), enhanced widget (shuffle counter, last-shuffle timestamp, Favorites quick action, history recording), NSFW purity wired through WallpaperRepository (reads from PreferencesManager), cleaned up AutoWallpaperWorker dead code (removed wikimedia/nasa branches + NasaRepository dep), phone preview live-updating clock (LaunchedEffect 60s ticker)
- v1.4.0: Exposed dual wallpaper split crop in wallpaper detail (DualWallpaperService), batch download all favorites (BatchDownloadService), fixed blur filter in wallpaper editor (downscale-upscale blur), sound auto-preview on detail enter (wired to Settings toggle), NSFW filter toggle in Settings (Wallhaven only, requires API key), auto-load "More Like This" similar sounds
- v1.3.0: Search history wired up (save searches, dropdown with recent queries, delete/clear), aspect ratio presets in crop screen (Free/9:16/16:9/1:1), long-press delete wallpaper favorites with undo, fix deprecated OpenInNew icon
- v1.2.0: Swipe-to-delete favorites with undo snackbar, wallpaper history tap-to-reapply navigation, hide skip on last onboarding page, improved empty states (contextual messages + action buttons), accessibility labels on wallpaper detail action buttons, download history type badges
- v1.1.0: Animated nav transitions (fade+slide), "More Like This" for all sounds (not just Freesound), favorite toggle snackbar feedback, clickable sound tags (search on tap), color filter clear/reset button, cache size display in settings, wallpaper detail loading/error states (SubcomposeAsyncImage), version bump
- v1.0.0: Freesound.org integration (600K+ sounds), dual-source sound search, duration-filtered IA queries, fixed broken trending tab, 10 sound categories, duration filter chips, "More Like This" similar sounds, fade in/out in audio trimmer, local file picker for custom ringtones, removed Wikimedia/NASA wallpaper sources, reordered wallpaper/sound tabs, source badges, tags display, scrollable sound detail
- v0.9.0: Share wallpaper/sound, wallpaper history screen, sound download, phone preview real time, source code link, licenses clickable, sounds shimmer, grid columns setting, editor error handling
- v0.8.0: ContactPicker ringtone wiring, cache eviction, crash logging, confirmation dialogs, category->search, database migrations, version sync
- v0.7.0: Initial full-featured release (7 sources, editors, widget, auto-wallpaper, dual wallpaper, video wallpaper, batch download, offline favorites, export/import)
