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
- **v4.0.0** (versionCode 30)
- Version strings in: `app/build.gradle.kts`, `SettingsScreen.kt` About section, `AppModule.kt` User-Agent, `README.md` badge

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
Room DB (v4): favorites, downloads, search_history, wallpaper_cache,
              wallpaper_history, ia_audio_cache, wallpaper_collections, wallpaper_collection_items
DataStore: Settings, Onboarding
```

## API Keys
- Zero API keys required for v3.0.0 (Pexels key hardcoded in PreferencesManager default)
- `WALLHAVEN_API_KEY` - Optional, higher rate limits + NSFW
- Freesound API was removed in v2.6.0

## Database Migrations
- v1->2: Added wallpaper_cache + wallpaper_history tables
- v2->3: Added ia_audio_cache table
- v3->4: Added wallpaper_collections + wallpaper_collection_items tables
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
- Signing credentials are hardcoded in build.gradle.kts (should move to local.properties)

## Key Files
- `FreeVibeApp.kt` - Application class, crash logging, cache eviction on startup
- `FreeVibeRoot.kt` - NavHost with animated transitions, bottom nav, Hilt EntryPoint
- `AppModule.kt` - Hilt DI module, OkHttp, Retrofit services, Room DB with migrations
- `SelectedContentHolder.kt` - Singleton: selectedWallpaper + wallpaperList + selectedSound + pendingCategoryQuery
- `WallpapersViewModel.kt` - Wallpaper state, tabs (Discover/Pexels/Reddit/Wallhaven/Unsplash/Color/Search)
- `WallpaperDetailScreen.kt` - VerticalPager with parallax, apply/download/edit/crop/share/collection actions
- `VideoWallpapersScreen.kt` - Video wallpaper browsing (Pexels + YouTube + Reddit), ExoPlayer per-card
- `VideoCropScreen.kt` - Landscape-to-portrait crop via yt-dlp FFmpeg
- `SoundsViewModel.kt` - Sound browsing, ExoPlayer playback, YouTube + IA sources
- `YouTubeRepository.kt` - NewPipe Extractor search + yt-dlp stream extraction, stream URL caching
- `SoundRepository.kt` - IA sound search, duration/category filtering
- `CollectionsScreen.kt` - Wallpaper collections list + detail grid
- `VideoWallpaperService.kt` - WallpaperService for live video wallpapers

## Known Issues (from audit)
- Pexels API key hardcoded as default in PreferencesManager (should use BuildConfig)
- SearchHistoryEntity PK is `query` alone — wallpaper/sound searches with same term collide
- WallpaperCacheEntity PK `id` can collide across different cache keys
- `WallpaperRepository.getWallhaven()` always passes empty API key (user preference never read)
- `evictExpired()` uses most conservative TTL for all sources (24h instead of per-source)
- `CollectionRepository.delete()` not transactional (items + collection in separate calls)
- No ForeignKey on wallpaper_collection_items.collectionId
- `streamCache` in YouTubeRepository grows unbounded (no TTL eviction)
- VideoWallpapersScreen creates ExoPlayer per card (memory pressure on scroll)
- KotlinJsonAdapterFactory adds ~2MB to APK (all adapters are KSP-generated, reflection not needed)
- HttpLoggingInterceptor always active (not gated behind BuildConfig.DEBUG)
- Fastlane metadata completely outdated (references Pixabay, NASA, Freesound, Stability AI)

## Version History
- v3.0.0+audit: Fix Discover tab navigation (share wallpaper list via SelectedContentHolder). Remove r/wallpaperengine. Fix 20+ bugs: FavoriteEntity valueOf crash, ContactPicker empty name crash, SoundEditorState hashCode, AudioTrimmer/WallpaperApplier/DualWallpaper/DownloadManager/SoundApplier/OfflineFavorites resource leaks, YouTubeRepository connection leak, VideoCropScreen OkHttpClient reuse, Reddit case-insensitive extensions, OfflineFavorites stale DB path on remove.
- v3.0.0: YouTube integration (NewPipe Extractor search + yt-dlp stream extraction). Video Wallpapers tab with ExoPlayer auto-playing cards, crop editor for landscape-to-portrait conversion, yt-dlp FFmpeg crop. VideoWallpaperService: file timestamp hot-swap, looping. 5 bottom nav tabs. Pexels as photo + video source. Reddit video sources. Search across all video sources. Orientation filtering.
- v2.7.0-v2.0.0: See memory file for full changelog.
