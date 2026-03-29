# Aura - CLAUDE.md

## Overview
Open-source Android app for device personalization. 21 content sources across wallpapers, video wallpapers, GIFs, and sounds. Weather effects, AMOLED editor, smart scheduler, Material You color preview.

## Tech Stack
- Kotlin 2.1.0 / Jetpack Compose / Material 3
- Hilt 2.53.1 DI, Room 2.6.1 DB, Retrofit 2.11.0 + OkHttp, Moshi + KSP
- Coil 2.7.0 (images), Media3 ExoPlayer (audio/video), WorkManager 2.10.0, Glance 1.1.1 (widget)
- NewPipe Extractor (YouTube search), yt-dlp (stream extraction + FFmpeg crop)
- Palette API (Material You color extraction), Open-Meteo (weather)
- Min SDK 26, Target SDK 35, JDK 17

## Build
```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug
```
Gradle 8.12 pinned via wrapper. AGP 8.7.3.

## Version
- **v4.5.0** (versionCode 35)
- Version strings in: `app/build.gradle.kts`, `SettingsScreen.kt` About section, `AppModule.kt` User-Agent, `VideoWallpapersScreen.kt` Reddit UA, `README.md` badge

## Architecture
```
Compose UI (16+ screens, 5 bottom nav tabs: Wallpapers, Videos, Sounds, Favorites, Settings)
  ViewModels (Hilt) + SelectedContentHolder singleton (bridges state between screens)
    Wallpaper sources: Wallhaven, Picsum, Pexels, Reddit (Discover = mixed Wallhaven+Picsum)
    Video sources: Pexels (primary), YouTube, Reddit (r/livewallpapers, r/LiveWallpaper, r/Amoledbackgrounds)
    Sound sources: YouTube (NewPipe + yt-dlp), Internet Archive (duration-filtered)
    Services: WallpaperApplier, SoundApplier, DownloadManager, AudioTrimmer,
              DualWallpaper, BatchDownload, ContactRingtone, FavoritesExporter,
              OfflineFavorites, WallpaperHistory, VideoWallpaperService, CollectionRepository
Room DB (v6): favorites, downloads, search_history, wallpaper_cache,
              wallpaper_history, ia_audio_cache, wallpaper_collections, wallpaper_collection_items
DataStore: Settings, Onboarding
```

## API Keys
- Pexels/Pixabay keys provided via BuildConfig (defaults baked in, overridable via local.properties)
- `WALLHAVEN_API_KEY` - Optional, higher rate limits + NSFW
- Freesound API was removed in v2.6.0

## Database Migrations
- v1->2: Added wallpaper_cache + wallpaper_history tables
- v2->3: Added ia_audio_cache table
- v3->4: Added wallpaper_collections + wallpaper_collection_items tables
- v4->5: Composite PKs for search_history (query+type) and wallpaper_cache (id+cacheKey)
- v5->6: ForeignKey CASCADE on wallpaper_collection_items.collectionId + index
- Migrations defined in AppModule.kt. Uses fallbackToDestructiveMigrationOnDowngrade() only.

## Gotchas
- `SelectedContentHolder` bridges wallpaper selection AND wallpaper list between screens (detail pager needs the list)
- `SelectedContentHolder.pendingCategoryQuery` is consumed on WallpapersViewModel init (set to null after read)
- Category navigation uses `saveState=false, restoreState=false` to force ViewModel recreation
- FreeVibeRoot uses Hilt EntryPoint (not ViewModel) to access SelectedContentHolder from Composable
- WallpaperDetailScreen gets its OWN WallpapersViewModel (different nav destination) — must use SelectedContentHolder for state
- Sound tabs use duration-specific queries: Ringtones 5-30s, Notifications 0-3s, Alarms 5-40s
- DurationFilter and SoundCategory enums are in SoundsViewModel.kt, not Models.kt
- AudioTrimmer fade is a lossy byte-level approximation on compressed MP3
- NASA/Wikimedia/Freesound enum values kept in ContentSource for legacy favorites compatibility
- NavHost uses animated transitions (fade+slide) in FreeVibeRoot
- Package is `com.freevibe` (cannot rename without breaking updates), display name is "Aura"
- Signing credentials in local.properties (not committed), build.gradle.kts reads from localProps

## Key Files
- `FreeVibeApp.kt` - Application class, crash logging, cache eviction on startup
- `FreeVibeRoot.kt` - NavHost with animated transitions, bottom nav, Hilt EntryPoint
- `AppModule.kt` - Hilt DI module, OkHttp, Retrofit services, Room DB with migrations
- `SelectedContentHolder.kt` - Singleton: selectedWallpaper + wallpaperList + selectedSound + pendingCategoryQuery
- `WallpapersViewModel.kt` - Wallpaper state, tabs (Discover/Pexels/Reddit/Wallhaven/Unsplash/Color/Search)
- `WallpaperDetailScreen.kt` - VerticalPager with parallax, apply/download/edit/crop/share/collection actions
- `VideoWallpapersScreen.kt` - Video wallpaper browsing (Pexels + YouTube + Reddit), single ExoPlayer for visible card
- `VideoCropScreen.kt` - Landscape-to-portrait crop via yt-dlp FFmpeg
- `SoundsViewModel.kt` - Sound browsing, ExoPlayer playback, YouTube + IA sources
- `YouTubeRepository.kt` - NewPipe Extractor search + yt-dlp stream extraction, stream URL caching (3h TTL)
- `SoundRepository.kt` - IA sound search, duration/category filtering
- `CollectionsScreen.kt` - Wallpaper collections list + detail grid
- `VideoWallpaperService.kt` - WallpaperService for live video wallpapers

## Known Issues (remaining)
- Fastlane metadata completely outdated
- YouTube videos still lack dimension metadata (NewPipe extractor doesn't expose w/h for search results), pass through orientation filter

## Version History
- v4.5.0: Video wallpaper orientation fixes. VideoWallpaperItem now carries videoWidth/videoHeight from APIs (Pexels, Pixabay, Reddit). Orientation filter now actually filters by dimensions (was keyword-only). Portrait/Landscape badges on video cards. VideoCropScreen uses MediaMetadataRetriever fallback with rotation awareness (was hardcoded 1920x1080). VideoWallpaperService: removed broken applyCenterCrop setFixedSize distortion, now uses screen-sized surface + native SCALE_TO_FIT_WITH_CROPPING. Confirm dialog warns on landscape video and promotes Crop as primary action. resolveScreenSize() uses WindowMetrics API for accurate screen dimensions.
- v4.4.0: WallpaperCropScreen: response leak fixed, cropped bitmap recycled on apply failure. VideoCropScreen: video dimension polling (was fixed 1s delay, now polls 3s), cache input file cleaned up after crop. SoundDetailScreen: DisposableEffect stops playback on screen exit, SimilarSoundsSection resets on soundId change. WallpaperEditorScreen response leak fixed.
- v4.3.0: Bitmap memory leaks fixed in WallpaperEditorScreen (intermediate bitmaps recycled between filter stages, old editedBitmap recycled on reset/reapply). VfxParticleRenderer: complete particle state reset on respawn (was missing alpha/size/phase/color), paint style reset at draw start. WeatherUpdateWorker: proper FINE vs COARSE location permission check (was using GPS with only COARSE). FreeVibeApp: crash log trim via RandomAccessFile seek instead of full 500KB readText(). WallpaperEditorScreen: HTTP response leak fixed in image download.
- v4.2.0: API keys moved to BuildConfig (overridable via local.properties). Signing credentials moved to local.properties. DB v6: ForeignKey CASCADE on wallpaper_collection_items (auto-cleanup on collection delete). VideoWallpapersScreen: single ExoPlayer for most-visible card (was per-card), response leaks fixed in applyVideoWallpaper. WallpapersViewModel: robust image extension detection. CollectionRepository.delete() simplified (FK CASCADE handles items).
- v4.1.0: Comprehensive audit fixes. DB v5: composite PK for search_history (query+type) and wallpaper_cache (id+cacheKey). Fix 16 bugs across data/service/UI layers.
- v3.0.0+audit: Fix Discover tab navigation. Remove r/wallpaperengine. Fix 20+ resource leaks and edge cases.
- v3.0.0: YouTube integration (NewPipe + yt-dlp). Video Wallpapers tab. VideoWallpaperService. 5 bottom nav tabs.
- v2.7.0-v2.0.0: See memory file for full changelog.
