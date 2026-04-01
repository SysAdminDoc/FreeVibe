# Aura - CLAUDE.md

## Overview
Open-source Android app for device personalization. Wallpapers, video wallpapers, and sounds from 20+ content sources. Weather effects, AMOLED editor, smart scheduler, Material You color preview. Community voting via Firebase, curated discovery, video wallpaper cropping via FFmpeg.

## Tech Stack
- Kotlin 2.1.0 / Jetpack Compose / Material 3
- Hilt 2.53.1 DI, Room 2.6.1 DB (v11), Retrofit 2.11.0 + OkHttp, Moshi + KSP
- Coil 2.7.0 (images), Media3 ExoPlayer (audio/video), WorkManager 2.10.0, Glance 1.1.1 (widget)
- NewPipe Extractor (YouTube search), yt-dlp (stream extraction), FFmpeg (video crop + audio fade/convert/normalize)
- Freesound v2 API (primary sound source, requires client_id), Openverse API (fallback, zero auth)
- Firebase Realtime Database (community voting + moderation)
- Palette API (Material You color extraction), Open-Meteo (weather)
- ML Kit Selfie Segmentation (parallax wallpaper depth effect)
- Min SDK 26, Target SDK 35, JDK 17

## Build
```bash
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" ./gradlew assembleDebug
```
Gradle 8.12 pinned via wrapper. AGP 8.7.3. SDK path in `local.properties` must point to `C:/Users/Xray/AppData/Local/Android/Sdk`.

## Version
- **v5.7.0** (versionCode 57)
- Version strings in: `app/build.gradle.kts`, `SettingsScreen.kt` About section, `AppModule.kt` User-Agent, `VideoWallpapersScreen.kt` Reddit UA, `README.md` badge

## Architecture
```
Compose UI (16+ screens, 5 bottom nav tabs: Wallpapers, Videos, Sounds, Favorites, Settings)
  ViewModels (Hilt) + SelectedContentHolder singleton (bridges state between screens)
    Wallpaper sources: Wallhaven, Picsum, Pexels, Pixabay, Bing Daily, Reddit (7 subs)
    Video sources: Pexels, YouTube, Reddit (4 subs), Pixabay
    Sound sources: Freesound v2 (primary), Openverse (fallback, zero auth), YouTube (NewPipe + yt-dlp)
    Services: WallpaperApplier, SoundApplier, DownloadManager, AudioTrimmer,
              DualWallpaper, BatchDownload, ContactRingtone, FavoritesExporter,
              OfflineFavorites, WallpaperHistory, VideoWallpaperService, CollectionRepository
    Firebase: VoteRepository (community votes, admin moderation, top voted leaderboard)
Room DB (v11): favorites, downloads, search_history, wallpaper_cache,
              wallpaper_history, wallpaper_collections, wallpaper_collection_items
DataStore: Settings, Onboarding, User Styles
```

## Sounds System (v5.0.0)
- **Sources**: Freesound v2 API (primary), Openverse (fallback, zero auth), YouTube (NewPipe + yt-dlp), SoundCloud (CC-licensed, optional client_id), Bundled (curated first-run), Community uploads (Firebase Storage + RTDB)
- **SoundCloud**: SoundCloudApi.kt + SoundCloudRepository.kt. CC-licensed tracks only. Orange "SC" badge. client_id from BuildConfig/local.properties (`soundcloud.client.id`). ContentSource.SOUNDCLOUD.
- **Community Uploads**: UploadRepository.kt. Firebase Storage (`sounds/{deviceId}/{timestamp}_{name}.mp3`) + RTDB (`community_sounds/{pushId}`). Upload FAB on SoundsScreen. Upload dialog with name + category picker. Progress indicator. ContentSource.COMMUNITY. Green "Community" badge. Community tab shows all uploads sorted by votes. 20MB file size limit.
- **Bundled Content**: BundledContentProvider (@Singleton) provides 10 ringtones + 10 notifications + 5 alarms with hardcoded Freesound preview URLs. Shown instantly before API results arrive. Gold "Aura Picks" badge. ContentSource.BUNDLED enum value.
- **Tabs**: Ringtones, Notifications, Alarms, YouTube, Community, Search (dynamic)
- **YouTube tab**: Dedicated search + paste URL import via NewPipe stream extractor
- **Top 5 This Week**: Weekly trending YouTube songs on Ringtones tab
- **One-tap apply**: Long-press any sound card for ringtone/notification/alarm/download sheet
- **Playback**: Single reusable ExoPlayer, real-time progress (50ms), waveform shown during playback only
- **Audio tools**: Lossless trim (MediaMuxer), fade (FFmpeg afade), normalize (FFmpeg loudnorm), convert (MP3/OGG/WAV/FLAC/M4A)
- **Apply**: SoundApplier downloads to MediaStore, sets via RingtoneManager. Contact-specific via ContactRingtoneService.
- **YouTube config**: Per-tab queries + blocked words list, user-configurable in Settings
- **Key files**: SoundsViewModel.kt, SoundsScreen.kt, SoundDetailScreen.kt, YouTubeRepository.kt, FreesoundV2Api.kt, BundledContentProvider.kt, SoundCloudApi.kt, SoundCloudRepository.kt, UploadRepository.kt

## Discovery Features
- **Wallpaper of the Day**: Hero card on Discover tab from Reddit top/day
- **Community Favorites**: Firebase top-voted wallpapers
- **Curated Collections**: Themed Wallhaven search chips
- **Trending Suggestions**: Popular search terms
- **Find Similar**: Wallhaven `like:{id}` + color search
- **Match My Theme**: Material You accent color search
- **Surprise Me**: Random Wallhaven wallpapers
- **Tag Chips + Color Palette**: Tappable on detail screen
- **Wallhaven Time Filters**: Today/Week/Month/6 Months/Year
- **Video Category Chips**: Nature, Abstract, Space, Neon, Ocean, Fire, etc.

## Video Wallpaper System
- Orientation detection from API dimensions + YouTube thumbnail proxy
- Crop via FFmpeg with LD_LIBRARY_PATH from yt-dlp reflection. FFmpeg.init() required at startup.
- Playback: SCALE_TO_FIT_WITH_CROPPING + setFixedSize(screenW, screenH)
- Crop viewport constrained to real screen pixel aspect ratio (WindowMetrics)

## API Keys
- Pexels/Pixabay keys via BuildConfig (defaults baked in, overridable via local.properties)
- Freesound API key via BuildConfig (`FREESOUND_API_KEY`)
- Wallhaven API key optional (higher rate limits + NSFW)

## Database Migrations
- v1->2: wallpaper_cache + wallpaper_history
- v2->3: ia_audio_cache
- v3->4: wallpaper_collections + wallpaper_collection_items
- v4->5: Composite PKs for search_history + wallpaper_cache
- v5->6: ForeignKey CASCADE on collection items + index
- v6->7: Drop ia_audio_cache (Internet Archive removed)
- v7->8: Add metadata columns (tags, colors, category, uploaderName, sourcePageUrl, fileSize, fileType, views, favoritesCount) to favorites table
- v8->9: Add colors, sourcePageUrl, views, favorites columns to wallpaper_cache table
- v9->10: Add missing indices on favorites.type and downloads.type

## Gotchas
- `SelectedContentHolder` bridges wallpaper/sound selection between screens (detail pager needs the list) — does NOT survive process death (in-memory singleton)
- `pendingCategoryQuery` consumed on WallpapersViewModel init (set to null after read)
- Category nav uses `saveState=false, restoreState=false` to force ViewModel recreation
- FreeVibeRoot uses Hilt EntryPoint (not ViewModel) to access SelectedContentHolder
- WallpaperDetailScreen gets its OWN WallpapersViewModel — uses SelectedContentHolder for state
- AudioTrimmer fade uses FFmpeg afade (MediaMuxer can't handle MP3 output)
- NASA/Wikimedia/INTERNET_ARCHIVE enum values kept in ContentSource for legacy favorites
- **YouTube stream cache TTL**: 6 hours
- **ExoPlayer for sounds**: Single reusable instance — NOT per-sound
- **FFmpeg.init()** MUST be called at startup or video crop + audio operations fail
- **libffmpeg.so** needs LD_LIBRARY_PATH from yt-dlp reflection
- **Firebase orderByChild** needs server-side indexOn — use client-side sort
- **Firebase RTDB refs MUST be lazy** — `FirebaseDatabase.getInstance()` throws if databaseURL missing from google-services.json. VoteRepository and UploadRepository use `by lazy` with try/catch.
- **Video crop viewport** constrained to real screen pixel aspect ratio (WindowMetrics)
- **r/Amoledbackgrounds** removed from all sources
- **Icons.Default.YouTube** doesn't exist — use `Icons.Default.SmartDisplay`
- **MutableStateFlow read-modify-write** — Always use `.update { it + newVal }` not `.value = .value + newVal` for thread safety in concurrent coroutines.
- **Bitmap.createBitmap** can return the same object as source — always check `!==` before recycling to avoid double-recycle crash.
- **MediaPlayer.stop() + release()** — Always separate into two try blocks; if stop() throws, release() must still be called.
- **DarkModeReceiver** is dead code — BroadcastReceiver never registered in manifest, SharedPreferences keys never written. TODO documented.
- **Nav route arguments** — All detail/picker routes use `Uri.encode(id)` to handle special characters in IDs.
- **Atomic file writes** — OfflineFavoritesManager and SoundEditorScreen use temp-then-rename pattern to prevent corrupt files on interruption.
- Package is `com.freevibe`, display name is "Aura"

## Key Files
- `FreeVibeApp.kt` - Application class, crash logging, cache eviction, yt-dlp + FFmpeg init
- `FreeVibeRoot.kt` - NavHost with animated transitions, bottom nav, Hilt EntryPoint, widget deep linking via `initialNavigateTo`
- `AppModule.kt` - Hilt DI, OkHttp, Retrofit services, Room DB v11 with migrations v1-v11
- `SelectedContentHolder.kt` - Singleton: selectedWallpaper + wallpaperList + selectedSound + pendingCategoryQuery
- `WallpapersViewModel.kt` - Wallpaper state, tabs, findSimilar, matchMyTheme, cached discover, loadJob cancellation
- `WallpapersScreen.kt` - Staggered grid, WOTD hero, collections, Community Favorites, time filters
- `WallpaperDetailScreen.kt` - VerticalPager, tag chips, color dots, find similar
- `WallpaperRepository.kt` - Aggregates all sources, per-source timeouts, cached discover
- `VideoWallpapersScreen.kt` - Video browsing (Pexels+YouTube+Reddit+Pixabay), orientation filter, inline VM
- `VideoCropScreen.kt` - FFmpeg crop with LD_LIBRARY_PATH reflection, timeout-guarded HTTP client
- `VideoWallpaperService.kt` - WallpaperService, center-crop playback, async prepare
- `ParallaxWallpaperService.kt` - ML Kit segmentation parallax, synchronized bitmap access
- `WeatherWallpaperService.kt` - Weather effects overlay, background image loading
- `SoundsViewModel.kt` - Sound state, YouTube/Freesound/Openverse, ExoPlayer, Top 5 This Week
- `SoundsScreen.kt` - Sound list, YouTube tab, one-tap apply sheet, snackbar feedback
- `SoundDetailScreen.kt` - Seekable waveform, apply as ringtone/notification/alarm, FlowRow tags
- `SoundEditorScreen.kt` - Waveform trim/fade/normalize/convert, undo stack, atomic file writes
- `FreesoundV2Api.kt` - Freesound v2 API (client_id auth, quality signals)
- `FreesoundRepository.kt` - Openverse source (zero auth fallback)
- `YouTubeRepository.kt` - YouTube search, stream cache (6h TTL), NewPipe + yt-dlp
- `AudioTrimmer.kt` - Trim (MediaMuxer for non-MP3, FFmpeg for MP3) + FFmpeg fade/normalize/convert
- `DownloadManager.kt` - Thread-safe StateFlow updates, orphaned MediaStore cleanup
- `VoteRepository.kt` - Firebase voting, ConcurrentHashMap for vote counts, transactional voter records
- `UploadRepository.kt` - Firebase Storage uploads, 20MB limit, lazy RTDB refs
- `FavoritesExporter.kt` - JSON export/import with full metadata, @Transaction bulk import
- `OfflineFavoritesManager.kt` - Atomic temp-then-rename file writes
- `DailyWallpaperWorker.kt` - Daily wallpaper rotation, intent extras for data passing
- `AutoWallpaperWorker.kt` - Auto wallpaper schedule, IOException-only retry
- `BatchDownloadService.kt` - Foreground service, CancellationException handling

## Known Issues
- Fastlane metadata outdated
- YouTube videos use thumbnail dimensions as orientation proxy
- Community Favorites only shows wallpapers in local Room cache
- SoundCloud requires API key registration (optional, graceful degradation)
- DarkModeReceiver is dead code (never registered, SP keys never written)
- SelectedContentHolder does not survive process death (in-memory singleton)

## Version History
- v5.7.0: Bug audit — process lifecycle, resource safety, null guards. **Critical**: AudioTrimmer FFmpeg process.destroy() in finally blocks for all 4 operations (trim/fade/normalize/convert); SoundEditorScreen playback/load job cancellation to prevent concurrent races; MediaPlayer listener cleanup before release (null all listeners first). **High**: VideoCropScreen FFmpeg process.destroy() in finally block; WallpaperApplier/VideoCropScreen null response body guards (throw instead of silent null). **Medium**: WallpaperDetailScreen empty pager safety (firstOrNull fallback); DailyWallpaperWorker bitmap recycle in try-finally; DownloadsScreen URI validation before opening (blank path check, file existence check for file:// URIs); FavoritesExporter import size check before processing (validates count before expensive mapNotNull).
- v5.6.0: Improvement audit — security, performance, UX. **Security**: SHA-256 hashed admin device IDs (no plaintext in APK); strict audio MIME whitelist for uploads (10 allowed types); upload name sanitization (length cap, empty check). **Performance**: DB indexes on favorites.addedAt, favorites.type+addedAt, downloads.downloadedAt, wallpaper_history.appliedAt (migration v10→v11); shared YouTube resolve semaphore (6 permits, reused across all resolve calls); replaced CopyOnWriteArrayList with synchronized ArrayList in sound loading (eliminates ~80 array copies per load). **UX**: "Resolving audio..." text indicator during YouTube URL resolution; categorized error messages (network/timeout/auth/rate-limit/server); accessibility contentDescription on 15+ icons across Sounds and Wallpapers screens. DB v10→v11.
- v5.5.0: Bug audit — 30+ fixes across 20 files. **Critical**: VideoWallpaperService onPrepared race on released MediaPlayer (clear listener before release); ParallaxWallpaperService bitmap recycle outside bitmapLock (native crash); AudioPlaybackManager release() didn't set stopped flag (zombie reconnection), added generation counter and main executor for callbacks; BatchDownloadService scope leak on cancel. **High**: NavHost missing bottom padding (content hidden behind nav bar); CollectionsScreen Flow leak per recomposition (cache with remember); WeatherWallpaperService bitmap race conditions (added bitmapLock + destroyed flag + isRecycled checks); DualWallpaperService OOM (streaming decode instead of double-buffer); SelectedContentHolder non-atomic dual assignment (@Synchronized); VideoWallpapersVM isLoading stuck on cancel (finally resets all flags); WallpapersVM stale state capture (re-read after suspension); FreesoundV2Repository reads user API key from PreferencesManager; AppModule writeTimeout + dynamic User-Agent via BuildConfig. **Medium**: SoundEditorState hashCode missing 6 fields; WallpaperCropVM premature bitmap recycle; WallpaperCropScreen/VideoCropScreen gesture state lost on config change (rememberSaveable); WallpapersScreen voteCounts Flow restart (derivedStateOf); WallhavenSearchResponse/RedditListingResponse null-safe defaults; WallpaperCacheManager @Transaction; WallpaperCacheDao chunked IN clause (500 limit); UploadRepository audio MIME validation. **Low**: DailyWallpaperWorker bitmap recycle; OfflineFavoritesManager debug logging; FavoritesVM .update{}; WallpapersVM randomOrNull; FavoritesScreen/DownloadsScreen rememberSaveable for tabs/sort.
- v5.4.0: Bug audit — 13 fixes. **Critical**: Wallpapers not loading (handleRouteFilters null==null dedup returned early on first call, never triggered loadWallpapers); Room DB crash on startup (missing indices on favorites.type, downloads.type, wallpaper_cache.cacheKey — added migration v9→v10). **High**: ParallaxWallpaperService bitmap race + segmenter leak; WeatherWallpaperService double-recycle crash; AudioPlaybackManager @Volatile; CollectionsScreen !! NPE; LicensesScreen/SettingsScreen unguarded startActivity. **Medium**: OfflineFavoritesManager renameTo fallback; FreeVibeWidget shuffle variety. **UI**: Compact header (removed verbose subtitle), small FABs (replaced wide labeled buttons with SmallFloatingActionButton), removed resolution text from cards, explicit tab labels. Version sync 5.2.0→5.4.0. DB v9→v10.
- v5.3.0: Comprehensive 4-round codebase audit — 121 bugs fixed. Crashes: bitmap double-recycle in ParallaxWallpaperService/WeatherWallpaperService/DualWallpaperService, Moshi Any? crash in WallhavenMeta, null-on-NOT-NULL in favorites metadata, AudioTrimmer MP3 muxer crash (now uses FFmpeg). Data-loss: favorite metadata not persisted through export/import, orphaned MediaStore entries in DownloadManager, non-atomic file writes in OfflineFavoritesManager/SoundEditorScreen, WallpaperHistory REPLACE clobbering timestamps. Race conditions: MutableStateFlow .update{} in 8+ files, loadJob cancellation in WallpapersViewModel/VideoWallpapersScreen, stale tab capture in SoundsViewModel. Navigation: missing NavType argument declarations, Uri.encode for special chars in route IDs. Services: VideoWallpaperService blocking prepare(), DailyWallpaperWorker clobbering SelectedContentHolder, AudioPlaybackManager stale controller callbacks. Security: scoped FileProvider paths, auto-backup exclusion rules. DB v7→v8→v9 (metadata columns on favorites + wallpaper_cache). Room indices on hot query columns.
- v5.2.0: Full codebase audit (78 source files). Fixed Sounds tab crash (Firebase RTDB lazy init in VoteRepository + UploadRepository). Fixed 20+ bugs: AudioPlaybackManager connection crash, bitmap leaks in DualWallpaperService/ParallaxWallpaperService/WeatherWallpaperService, VideoWallpaperService player release leak, SoundEditorScreen blocking MediaPlayer.prepare, race conditions on MutableStateFlow updates (VideoWallpapersScreen + SoundsViewModel), community tab double-collect leak, ContactPicker empty URL crash, recycled bitmap access in ParallaxWallpaperService segmentation callback.
- v5.1.0: Added parallax wallpapers, bundled content, SoundCloud, community uploads, MediaSession audio service.
- v5.0.0: Sounds overhaul (YouTube tab, one-tap apply, Top 5 This Week, paste URL import). Removed AI wallpaper generation, Internet Archive, KlipyApi, SoundCollections dead code. Onboarding style picker. Full codebase audit: fixed AudioTrimmer stream leaks, DualWallpaperService bitmap leak, DownloadManager progress calc, removed r/Amoledbackgrounds from defaults. DB v7.
- v4.5.0: Major discovery + sounds overhaul + video wallpaper fixes + startup performance.
- v4.4.0: Response leaks, crop fixes, DisposableEffect cleanup.
- v4.3.0: Bitmap leaks, VFX reset, weather permission check.
- v4.2.0: API keys to BuildConfig, signing to local.properties, DB v6.
- v4.1.0: Comprehensive audit, DB v5 composite PKs, 16 bug fixes.
- v3.0.0: YouTube integration, Video Wallpapers tab, 5 bottom nav tabs.
