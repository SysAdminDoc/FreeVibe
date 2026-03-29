# Aura - CLAUDE.md

## Overview
Open-source Android app for device personalization. 21+ content sources across wallpapers, video wallpapers, GIFs, and sounds. Weather effects, AMOLED editor, smart scheduler, Material You color preview. Community voting via Firebase, curated discovery, video wallpaper cropping via FFmpeg.

## Tech Stack
- Kotlin 2.1.0 / Jetpack Compose / Material 3
- Hilt 2.53.1 DI, Room 2.6.1 DB, Retrofit 2.11.0 + OkHttp, Moshi + KSP
- Coil 2.7.0 (images), Media3 ExoPlayer (audio/video), WorkManager 2.10.0, Glance 1.1.1 (widget)
- NewPipe Extractor (YouTube search), yt-dlp (stream extraction), FFmpeg (video crop via bundled libffmpeg.so)
- Firebase Realtime Database (community voting + moderation)
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
    Wallpaper sources: Wallhaven, Picsum, Pexels, Pixabay, Bing Daily, Reddit (7 subs)
    Video sources: Pexels, YouTube, Reddit (r/livewallpapers, r/LiveWallpaper, r/Cinemagraphs, r/perfectloops), Pixabay
    Sound sources: YouTube (NewPipe + yt-dlp), Internet Archive (duration-filtered)
    Services: WallpaperApplier, SoundApplier, DownloadManager, AudioTrimmer,
              DualWallpaper, BatchDownload, ContactRingtone, FavoritesExporter,
              OfflineFavorites, WallpaperHistory, VideoWallpaperService, CollectionRepository
    Firebase: VoteRepository (community votes, admin moderation, top voted leaderboard)
Room DB (v6): favorites, downloads, search_history, wallpaper_cache,
              wallpaper_history, ia_audio_cache, wallpaper_collections, wallpaper_collection_items
DataStore: Settings, Onboarding
```

## Discovery Features (v4.5.0)
- **Wallpaper of the Day**: Hero card on Discover tab, full-width image + gradient overlay. Source: Reddit top/day from r/wallpapers + r/MobileWallpaper.
- **Community Favorites**: Top upvoted wallpapers (from Firebase votes across all tabs) shown at top of Discover. Resolves cached wallpapers by ID.
- **Curated Collections**: Horizontal carousel of themed Wallhaven search chips (AMOLED Black, Minimal, Nature 4K, Cyberpunk, Space, etc.)
- **Trending Suggestions**: Popular search terms shown on Discover (nature 4k, dark aesthetic, retro wave, studio ghibli, etc.)
- **Find Similar**: ImageSearch button on detail screen — Wallhaven `like:{id}` + color-similar search
- **Match My Theme**: FAB extracts Material You accent color (Android 12+), searches Wallhaven by hex
- **Surprise Me**: Shuffle FAB loads random wallpapers from Wallhaven `sorting=random`
- **Tag Chips**: Wallhaven tags shown as tappable chips on detail screen
- **Color Palette Dots**: Wallpaper colors shown as tappable circles on detail screen, tap to search
- **Wallhaven Time Filters**: Today/Week/Month/6 Months/Year chips on Wallhaven tab
- **Video Category Chips**: Nature, Abstract, Space, Neon, Ocean, Fire, Cinemagraph, Sci-Fi, Rain, Clouds

## Video Wallpaper System
- **Orientation detection**: VideoWallpaperItem carries videoWidth/videoHeight from Pexels, Pixabay, Reddit, YouTube (thumbnail dimensions via NewPipe Image class)
- **Orientation filter**: Filters by actual dimensions. YouTube also uses orientation-specific search queries.
- **Crop**: VideoCropScreen constrained to real screen pixel aspect ratio (WindowMetrics API). FFmpeg called via ProcessBuilder with LD_LIBRARY_PATH from yt-dlp reflection. Requires `FFmpeg.init()` at app startup (extracts shared libs from libffmpeg.zip.so).
- **Playback**: VideoWallpaperService uses `setFixedSize(screenW, screenH)` + `SCALE_TO_FIT_WITH_CROPPING`. Screen size via WindowMetrics API with legacy fallback.
- **Portrait/Landscape badges** on video cards. Landscape videos promote Crop as primary action in confirm dialog.

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
- **FFmpeg.init()** MUST be called at app startup alongside YoutubeDL.init() — without it, shared libs (libavcodec, libavdevice) are never extracted and video crop fails with linker errors
- **Firebase orderByChild** requires server-side `.indexOn` rules — use client-side sort with `.get().await()` instead
- **libffmpeg.so** cannot be executed directly from APK native lib dir — needs LD_LIBRARY_PATH pointing to extracted shared libs in yt-dlp packages dir
- **Video crop viewport** must be constrained to real screen pixel aspect ratio (WindowMetrics), not layout-available space
- **r/Amoledbackgrounds** removed from all sources (wallpaper, video, daily pick)

## Key Files
- `FreeVibeApp.kt` - Application class, crash logging, cache eviction, yt-dlp + FFmpeg init
- `FreeVibeRoot.kt` - NavHost with animated transitions, bottom nav, Hilt EntryPoint
- `AppModule.kt` - Hilt DI module, OkHttp, Retrofit services, Room DB with migrations
- `SelectedContentHolder.kt` - Singleton: selectedWallpaper + wallpaperList + selectedSound + pendingCategoryQuery
- `WallpapersViewModel.kt` - Wallpaper state, tabs, findSimilar, loadRandom, matchMyTheme, fetchTopVoted, searchByTag
- `WallpapersScreen.kt` - Staggered grid, WOTD hero card, curated collections, trending, Community Favorites section, time filter chips
- `WallpaperDetailScreen.kt` - VerticalPager with parallax, tag chips, color palette dots, find similar button
- `VideoWallpapersScreen.kt` - Video browsing (Pexels + YouTube + Reddit + Pixabay), orientation filter, category chips, single ExoPlayer
- `VideoCropScreen.kt` - Screen-ratio-constrained crop via FFmpeg direct call with LD_LIBRARY_PATH reflection
- `VideoWallpaperService.kt` - WallpaperService, center-crop via SCALE_TO_FIT_WITH_CROPPING + screen-sized surface
- `VoteRepository.kt` - Firebase community votes, getTopVotedIds (client-side sort), admin moderation
- `WallpaperRepository.kt` - Aggregates all wallpaper sources, findSimilar (Wallhaven like:), getRandomWallhaven, searchByColor
- `RedditRepository.kt` - 7 wallpaper subs, after-token pagination, getDailyTopWallpaper
- `WallpaperCacheManager.kt` - Room cache with getByIds for resolving top voted wallpapers

## Reddit Wallpaper Sources
- Wallpapers: r/wallpapers, r/MobileWallpaper, r/wallpaper, r/WQHD_Wallpaper, r/MinimalWallpaper, r/phonewallpapers, r/iWallpaper
- Videos: r/livewallpapers, r/LiveWallpaper, r/Cinemagraphs, r/perfectloops
- Removed: r/Amoledbackgrounds (from all sources)

## Known Issues (remaining)
- Fastlane metadata completely outdated
- YouTube videos use thumbnail dimensions as orientation proxy (NewPipe doesn't expose video w/h in search results)
- Community Favorites only shows wallpapers that exist in local Room cache (browsed before)

## Version History
- v4.5.0: Major discovery overhaul + video wallpaper fixes. 7 discovery features (Find Similar, curated collections, Match My Theme, video categories, tag chips, trending, shuffle). WOTD hero card. Wallhaven toplist time filters. Community Favorites section (Firebase top voted). Video orientation detection from API dimensions + thumbnail proxy. Video crop: FFmpeg.init() fix, direct FFmpeg via ProcessBuilder with LD_LIBRARY_PATH, screen-ratio-constrained viewport. VideoWallpaperService: center-crop via SCALE_TO_FIT_WITH_CROPPING + screen-sized surface. 7 Reddit wallpaper subs (was 2). Removed r/Amoledbackgrounds. Endless scroll on all tabs. Color palette dots + tag chips on detail screen.
- v4.4.0: WallpaperCropScreen: response leak fixed, cropped bitmap recycled on apply failure. VideoCropScreen: video dimension polling (was fixed 1s delay, now polls 3s), cache input file cleaned up after crop. SoundDetailScreen: DisposableEffect stops playback on screen exit, SimilarSoundsSection resets on soundId change. WallpaperEditorScreen response leak fixed.
- v4.3.0: Bitmap memory leaks fixed in WallpaperEditorScreen. VfxParticleRenderer: complete particle state reset. WeatherUpdateWorker: proper location permission check. FreeVibeApp: crash log trim. WallpaperEditorScreen: HTTP response leak fixed.
- v4.2.0: API keys moved to BuildConfig. Signing to local.properties. DB v6 FK CASCADE. Single ExoPlayer for video cards.
- v4.1.0: Comprehensive audit fixes. DB v5 composite PKs. Fix 16 bugs.
- v3.0.0+audit: Fix Discover tab navigation. Remove r/wallpaperengine. Fix 20+ resource leaks.
- v3.0.0: YouTube integration (NewPipe + yt-dlp). Video Wallpapers tab. VideoWallpaperService. 5 bottom nav tabs.
