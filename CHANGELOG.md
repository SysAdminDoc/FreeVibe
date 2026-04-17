# Changelog

All notable changes to Aura will be documented in this file.

## v6.9.0
- Round 17 audit — last-mile download caps
- **Bounds**: `ColorExtractor.extractFromUrl` caps buffered response at 32 MB (palette tinting only needs a 200×200 downsample; a hostile redirect to a giant image would otherwise balloon the heap just for widget tint extraction). Also hardened `calculateSampleSize` against `sample` integer overflow on pathological near-Int.MAX dimensions
- **Bounds**: `SoundApplier.saveUrlToMediaStore` caps downloads at 64 MB (matches `DownloadManager`). Previously a misresolved URL returning an endless stream could write to MediaStore until the user's storage filled

## v6.8.0
- Round 16 audit — video cropper hardening, offline-cache bounds, preferences consistency
- **Safety**: `VideoCropScreen` HTTP download for remote crop input now caps at 256 MB (Content-Length + streamed). Local file paths are validated with `File.exists() + canRead()` before handing to FFmpeg (previously surfaced as cryptic "Invalid data found" errors)
- **Resources**: `VideoCropScreen` FFmpeg process now uses a 4 KB bounded drain for its merged stdout/stderr instead of `readText()` — a chatty ffmpeg run could previously allocate MBs of String data just to log the last 500 chars
- **Structured concurrency**: `VideoCropScreen` outer and inner catch blocks now rethrow `CancellationException`
- **Bounds**: `OfflineFavoritesManager.cacheOffline` enforces an 80 MB per-file ceiling (in addition to the existing 512 MB total budget) so one hostile favorite URL can't blow the whole offline cache in a single download. Also added `CancellationException` rethrow
- **Bounds**: `SoundEditorViewModel.downloadToCache` caps audio downloads at 96 MB — the editor is for short clips, and a misresolved YouTube URL previously could fill cacheDir while the user waits
- **Consistency**: `PreferencesManager.setVideoFpsLimit` / `setVideoPlaybackSpeed` now write SharedPreferences FIRST, then DataStore. `VideoWallpaperService` (which can only read SharedPreferences because WallpaperService can't easily subscribe to DataStore) always sees the new value even if the suspending DataStore write is cancelled mid-flight. Previously the opposite order could leave the runtime service stale for the remainder of its lifetime

## v6.7.0
- Round 15 audit — deeper sweep across bitmap download paths, locale correctness, intent safety, and startup hardening
- **Safety**: `WallpaperApplier.downloadBitmap` and `DualWallpaperService.downloadBitmap` now enforce a 64 MB ceiling on the buffered byte array (Content-Length + actual size) so a hostile CDN can't OOM us during decode
- **Safety**: `DailyWallpaperWorker` notification-thumbnail download now caps at 4 MB + propagates `CancellationException` (previously swallowed, which let a cancelled worker continue allocating)
- **Reliability**: `WeatherWallpaperService.scaleBitmap` no longer leaks the intermediate `scaled` bitmap when `Bitmap.createBitmap(scaled, x, y, …)` throws, and now uses the real `scaled.width/height` consistently (previous code computed crop coordinates from a theoretical value that could diverge from the actual bitmap size, causing slightly off-center crops)
- **Startup**: `FreeVibeApp.warmCommunityIdentity` is now try/caught so a Firebase-auth failure at boot can't reach the uncaught-exception handler and crash the app (CancellationException still propagates)
- **Locale.ROOT sweep**: `AutoWallpaperWorker.normalizeWallpaperRotationSource` (Turkish locale broke source comparison), `SoundQuality` source-name titlecase, `WallpaperDetailScreen` file-type uppercase + `formatCompactCount` + `formatFileSizeLabel`, `SettingsViewModel.formatBytes`, `SharedComponents.formatBytes`, `SoundEditorScreen.formatMs` timestamp, `WallpaperCropScreen`/`VideoCropScreen` zoom-percent, `WallpaperEditorScreen` slider value. All machine-use numeric formatting now uses `Locale.ROOT` so non-English locales don't substitute commas or non-Latin digits
- **Intent safety**: `SettingsScreen.openNotificationSettings` falls back to app-details when an OEM Android build doesn't expose `ACTION_APP_NOTIFICATION_SETTINGS` (previously crashed with ANFE on some MIUI/EMUI devices). `SoundDetailScreen` + `WallpaperDetailScreen` share buttons now skip empty share URLs (was opening a blank share sheet) and wrap `startActivity` in try/catch. `ContactPickerScreen` "Open Settings" wrapped in try/catch
- **Schema resilience**: `WallhavenWallpaper.id` and `url` fields now have `""` defaults, so a malformed Wallhaven response (null id/url) yields a filterable Wallpaper with blank fields instead of a JsonDataException that kills the whole page

## v6.6.0
- Round 14 audit — reliability, safety, resource bounds, and unit-test recovery
- **Security/safety**: `DownloadManager` now enforces a 64 MB ceiling per file for both images and audio (rejects both Content-Length-advertised and streamed overruns) to prevent a malicious/broken server from filling storage
- **Reliability**: `ParallaxWallpaperService` no longer double-closes the ML Kit segmenter when a new image arrives before the previous segmentation callback fires (tracked per-segmenter with explicit nulling + synchronized guard in success/failure listeners)
- **Reliability**: `ParallaxWallpaperService.scaleBitmapCenterCrop` no longer leaks the intermediate `scaled` bitmap when `Bitmap.createBitmap(scaled, x, y, …)` throws OOM/IllegalArgument
- **Reliability**: `VideoWallpaperService` now tracks the last-played path in addition to `lastModified`, so picking a different video file that happens to share the previous file's timestamp triggers re-init instead of silently keeping the old stream
- **Resources**: `AudioTrimmer` replaced four unbounded `readText()` calls on FFmpeg's merged stdout/stderr with a bounded drain (8 KB chunks, unlimited reads but no retention) — previously a chatty FFmpeg run could allocate MBs of throwaway String data
- **Structured concurrency**: Added missing `CancellationException` rethrow across 8 more catch sites — `FreeVibeWidget` (OpenCurrentWallpaper, applyFromSource, applyRandom), `WallpapersViewModel` (loadWallpapers, findSimilar), `ContactPickerViewModel` (search), `VoteRepository` (moderateHide, getTopVotedIds), `FavoritesExporter.parseJson`
- **Cleanup**: Removed unused `Canvas`/`Matrix`/`Paint`/`SurfaceTexture` imports from `VideoWallpaperService`
- **Testability**: `MainActivity.isAllowedLaunchUrl` now uses pure-JVM scheme extraction instead of `android.net.Uri.parse`, so launch-URL validation is directly unit-testable (was previously broken in local unit tests with a "Method parse in android.net.Uri not mocked" runtime failure)
- **Tests**: Fixed pre-existing `MainActivityLaunchNavigationTest.buildLaunchWallpaper preserves wallpaper metadata` failure; updated `FavoritesExporterValidationTest` to match v6.5.0's HTTPS-only policy; added a new test covering unsafe launch-URL rejection (http/file/content/javascript schemes); 151 total unit tests pass.

## v6.5.3
- Fix adaptive icon support: generate proper 108dp foreground PNGs, circular round icons, restore mipmap-anydpi-v26 XML wrappers
- Remove orphaned vector icon drawables that didn't match brand

## v6.5.2
- Restore original glowing-A beam icon from v6.1.0 across all mipmap densities

## v6.5.1
- Restore original adaptive vector app icon (reverts PNG logo changes from v6.2.0)

## v6.5.0
- Security: OOM-safe bitmap decode, HTTPS-only URL validation, SoundUrlResolver HTTP fix
- Correctness: CancellationException rethrow in 8 more catch sites
- Accessibility: IconButton touch targets to 36dp minimum (8 targets)
- Performance: remember() wrapping, regex hoisting, LaunchedEffect key fixes

## v6.4.0
- Structured concurrency audit: CancellationException sweep across 16 catch sites, 4 ViewModels

## v6.3.0
- Upload/download security hardening, UI polish pass

## v6.2.0
- Undo correctness, preview-apply stability

## v6.1.0
- Video preview, adaptive widget tint, parallax from gallery, collection sharing

## v6.0.0
- Undo, Widget preview, Bulk favorites, Preview mode, Collection rotation

## v5.26.0
- ModifierParameter lint cleanup

## v5.25.0
- UI state @Immutable, DailyWallpaperWorker backoff

## v5.24.0
- Compose stability, HTTPS enforcement, API key input sanitization

## v5.23.0
- Coil disk cache, shared OkHttp, crossfade

## v5.22.0
- Final locale sweep, remaining Regex hoisting, ProGuard verified

## v5.21.0
- Regex hoisting, dead code removal, perf

## v5.20.0
- Parallax atomicity, bitmap decode safety, widget feedback, locale
