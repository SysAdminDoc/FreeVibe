# Changelog

All notable changes to Aura will be documented in this file.

## Unreleased

## v6.23.0
- **Video wallpaper presentation controls**: online video apply now offers Fill and Fit before setup. Fill keeps the premium full-screen crop behavior, while Fit preserves the complete frame with letterboxing.
- **Runtime scale-mode support**: `VideoWallpaperService` now reads the selected scale mode for both MediaPlayer videos and canvas-rendered GIF wallpapers.
- **Roadmap closure**: Phase 5.1 fit/fill/crop controls are now represented in the apply flow; remaining video work is the deeper loop trim/timeline editor.

## v6.22.0
- **Local video/GIF wallpapers**: Video Wallpapers and Settings now open a single system picker for local `video/*` clips and animated GIFs, then copy the selection into Aura-managed storage for live wallpaper setup.
- **Animated GIF live wallpaper playback**: `VideoWallpaperService` now detects `.gif` selections and renders them through a bounded canvas loop while keeping the existing MediaPlayer path for videos.
- **Import UX cleanup**: removed the dead "GIF not supported" Settings entry, updated gallery actions and fallback toasts to use motion-wallpaper copy, and expanded storage tests for GIF/MOV/MKV extension handling.

## v6.21.0
- **YouTube-only sound feed**: Sounds browsing and in-tab search now use YouTube results only. Audius is removed from the active Sounds experience and user-facing source copy; legacy Freesound attribution remains only for older saved content.
- **Intent-specific YouTube discovery**: default sound searches now seed from `Ringtones`, `Notifications`, and `Alarms`, then add one precise sound-effect query per tab. Duration filters clamp notifications to very short clips, keep alarms short and direct, and avoid long ringtone compilations.

## v6.20.0
- **Wallpaper detail overlay placement**: the compact wallpaper apply card now sits lower on devices with three-button navigation, using the previously empty bottom inset so more of the wallpaper remains visible above the controls.

## v6.19.0
- **Wallpaper detail visibility**: opening a wallpaper detail now keeps the image visible by default with a compact bottom action card instead of the full metadata/apply panel covering the wallpaper. Dense metadata, palette, tags, and extended actions remain available from Details, with a clear Show image action to collapse the panel again.

## v6.18.0
- **Sound source cleanup**: Sounds browsing and search now aggregate only Freesound, Audius, and YouTube, removing Aura Picks/bundled results, ccMixter, SoundCloud, and the old Openverse fallback from the user-facing sound feed.
- **YouTube readiness**: Opening or clearing the YouTube sounds tab now loads a default YouTube query automatically, and YouTube play buttons show a loading spinner plus clearer copy while the stream resolves or buffers before playback starts.

## v6.17.0
- **Secondary-flow premium polish**: refined the post-browse UX for sound detail, contact assignment, wallpaper preview, video preview, wallpaper edit/crop, and sound trim/edit flows so recovery, permission, loading, empty, and action states now match the v6.16 design system instead of falling back to ad hoc centered spinners and copy.
- **First-run finish**: tightened onboarding status labels, feature badges, page indicators, and navigation buttons to the same rectangular 4-12dp shape language, and simplified the first welcome page so the primary CTA is not crowded by a partially visible feature card.
- **Contact ringtone assignment**: replaced plain permission prompts and empty contact views with shared `AuraStateCard` recovery affordances, upgraded contact search to the shared compact search field, switched circular initials to rectangular avatars, and added per-contact applying feedback while assignments run.
- **Sound detail quality**: upgraded the waveform play control, permission warning, secondary action row, disabled share handling, similar-sound skeleton loading, and empty-similar state for clearer hierarchy, stronger touch targets, and more trustworthy feedback.
- **Editor and preview consistency**: brought wallpaper editor/crop and sound editor unavailable/loading/first-run states onto the shared state-card pattern, tightened chip/button radii to the 4-12dp system, clarified audio editor microcopy, and normalized preview action controls.

## v6.16.0
- **Premium UX polish pass**: tightened Aura's Compose design system around neutral AMOLED surfaces, brass/mist/coral accents, rectangular 4-12dp radii, zero letter spacing, calmer elevation, and no pill-shaped status backdrops.
- **Navigation and component consistency**: removed decorative gradient orbs from the app root, replaced Material pill indicators/badges with quieter rectangular count badges, normalized `GlassCard`, `HighlightPill`, search dropdowns, bottom navigation, cards, settings rows, sheets, preview surfaces, and diagnostic metrics.
- **States and feedback**: added a shared `AuraStateCard` pattern and applied it to wallpapers, sounds, video wallpapers, downloads, favorites, collections, wallpaper history, and loading/error/empty states so recovery copy, actions, icons, and spacing feel consistent.
- **Workflow clarity**: clarified the wallpaper generation entry point, improved sound/video retry and gallery fallback affordances, improved API-key visibility accessibility copy, and made empty states explain the next useful action instead of stopping at "nothing here."
- **Verification**: `assembleDebug`, `testDebugUnitTest`, and `lintDebug` are green. USB install was not performed because the connected phone already has `com.freevibe` installed with a different signing key; uninstalling it would remove or disturb the user's installed app.

## v6.15.0
- **Deep audit pass** — eleven real bugs found in the v6.13–v6.14 deltas (AI wallpaper, Phase 6.2 dark/light auto-switch, Phase 6.4 adaptive tint, Phase 2.5 seasonal/Pexels). All fixes ship with unit-test regression nets.
- **Data integrity (P0)**: `WeatherUpdateWorker` was storing latitude/longitude with `putLong(value.toLong())` — silently truncating fractional degrees. A user at 39.7392° was stored as `39`, a user near the equator at 0.5° was stored as `0`. The reader then used the `tintLat != 0.0 && tintLon != 0.0` sentinel to gate adaptive tinting, so anyone within 1° of Null Island had tinting disabled entirely. Switched to `putFloat` (~7 sig figs, sub-meter precision) plus a `location_present` boolean sentinel. Reader falls back to the legacy Long keys for a single update cycle so existing installs don't lose tinting between upgrade and the next 30-min worker tick.
- **Correctness (P1)**: `SolarCalculator.sunTimes` default UTC-offset arg used `TimeZone.getDefault().rawOffset` which ignores DST. Every region observing daylight saving had sunrise/sunset shifted by an hour for ~half the year, which the adaptive-tint phase math depends on. Switched to `getOffset(System.currentTimeMillis())`.
- **Battery + correctness (P1)**: `SystemThemeListener` (Phase 6.2 new code) ran a 500 ms `while (true)` polling loop that (a) never stopped when the user disabled auto-switch, (b) trapped the outer flow-collector so further preference emissions couldn't propagate, and (c) woke the CPU twice a second forever. Replaced with `ComponentCallbacks.onConfigurationChanged` — an actual event, delivered even while the app is fully backgrounded.
- **Reliability (P1)**: `SystemThemeListener.applyStoredWallpaper` called `WallpaperApplier.applyFromUrl`, which only speaks HTTP — `OkHttp.Request.Builder().url(...)` throws `IllegalArgumentException` for `file://` or `content://` schemes. Users whose last applied wallpaper was AI-generated (`file:/data/.../foo.png`) silently lost auto-switch. New `WallpaperApplier.applyByLocator` dispatches on scheme: http(s) → existing OkHttp path, file/content/absolute-path → bounded two-pass `BitmapFactory` decode with the same 64 MB ceiling and `inSampleSize` sampling.
- **Storage leak (P1)**: `AiWallpaperRepository.pruneOldFiles` was defined but never called — the 50-image cap was a promise, not an enforcement. Now invoked after every successful generation. Also sweeps stale `.tmp` files left by interrupted writes.
- **Thread safety + responsiveness (P1)**: `AiWallpaperViewModel.applyWallpaper` decoded the full-resolution PNG via `BitmapFactory.decodeFile` on the Main coroutine context (a 3–4 MB PNG → ~10 MB bitmap synchronously on the UI thread). Re-routed through `applyByLocator` so the disk read + decode + sampling all happen on `Dispatchers.IO`.
- **Structured concurrency (P2)**: `AiWallpaperRepository.generate` wrapped the body in `runCatching` which captures `CancellationException`. A back-navigation mid-generation was surfaced as a generic error message instead of a clean coroutine teardown. Switched to explicit `try`/`catch` with cancellation rethrow.
- **Performance (P2)**: `WeatherWallpaperService.draw` allocated a new `ColorMatrix` + `Paint` every frame at 30 FPS whenever adaptive tint was enabled (~30 allocations/sec under steady-state). Cached the `Paint` by 5-minute time bucket; only rebuilds when the bucket changes. Also short-circuits to the no-tint draw path during the neutral-midday window.
- **UX (P2)**: Settings dark/light mode wallpaper slot opened an `AlertDialog` only when wallpaper history was non-empty — a fresh install / cleared history made the slot affordance a dead click. Now opens regardless and shows a "No wallpapers applied yet" explanatory empty state with guidance.
- **UX (P3)**: Settings VFX picker confirm button was labeled "Cancel" even though each radio click already committed synchronously. Relabeled to "Close". Mirrored to the dark/light slot picker.
- **Error messages (P2)**: `AiWallpaperRepository` now maps Stability AI HTTP codes (401/402/403/422/429/5xx) to actionable user copy ("API key invalid", "Out of credits", "Content policy", "Rate limited") instead of "Generation failed (HTTP 429): {raw JSON}".
- **Maintenance**: Hoisted the per-call `\\s+` regex in `AiWallpaperRepository` to a file-level constant. Restored DRY between WallpaperApplier's HTTP and local decode paths via shared `computeSampleSize`.
- **Tests**: 30 new unit tests across `SolarCalculatorTest` (10 — DST regression, polar day/night clamps, equinox day length, golden-hour tint band, intensity scaling), `AiWallpaperRepositoryFriendlyErrorTest` (10 — per-status-code copy, body-append rules), `WallpaperLocatorSchemeTest` (10 — http/file/content/path/unknown classification, case-insensitivity, three-part split with URLs containing pipes). Fixed pre-existing `SettingsViewModelTest` fixture gap (missing mocks for `adaptiveTintIntensity`, `darkModeWallpaperId`, `lightModeWallpaperId`, `stabilityAiKey` added in v6.13/6.14). 248/248 unit tests green.

## v6.14.0
- **AI Wallpaper Generation (Phase 3.1)**: New dedicated screen accessible via the "AI" chip in the Wallpapers header row. Enter a text prompt, pick a style (Photographic, Anime, Digital Art, Cinematic, Fantasy, Neon, Pixel Art, or None), and generate a 9:16 PNG via the Stability AI API. The result can be set as Home screen, Lock screen, or Both, and saved to Favorites. API key is entered in-screen (animated field, password-masked) and persisted in DataStore. Generated images are stored in `filesDir/ai_wallpapers/` with automatic pruning to the 50 most recent.
- **ContentSource.AI_GENERATED**: New enum value in `ContentSource`; `sourceDisplayName()` updated to return "AI Generated".
- **Version fix**: `build.gradle.kts` was still at 6.12.0/versionCode 92 despite the 6.13.0 commit. Bumped directly to 6.14.0/versionCode 94 since Phase 3.1 lands here.
- **ROADMAP cleanup**: Marked Phase 2.4 "Change your style" Settings entry and Phase 5.3 VFX Particle Overlays as done — both were already implemented in prior sessions but left unchecked.

## v6.13.0
- **Seasonal content**: `SeasonalContentManager` provides date-driven themes — Holiday (Dec), Halloween (Oct 15–31), New Year (Jan 1–3), Valentine (Feb 10–14), Summer (Jun 21–Sep 1). Returns null off-season; fully injectable singleton.
- **Sounds tab seasonal carousel**: When a seasonal theme is active, a `SoundCollectionSpec` with the seasonal query and amber-gold `SEASONAL` tone is prepended to the sound collection carousel on all three tone tabs (Ringtones, Notifications, Alarms).
- **Wallpapers Discover seasonal banner**: A `SeasonalBannerCard` full-line item appears in the staggered grid between the daily pick hero and the curated collection shortcuts. Tapping it searches for the seasonal wallpaper query.
- **Style-personalized Discover feed**: `WallpaperRepository.getDiscover()` now accepts `userStyles` from the user's onboarding preferences. When styles are non-empty, an additional style-biased Wallhaven search runs alongside the toplist, widening the feed toward the user's aesthetic preferences.
- **ROADMAP reconciliation**: Marked 1.2 (Freesound v2), 1.3 (SoundCloud CC), 1.4 (Drop IA), 2.3 (QuickApplySheet), 2.6 (Sound Detail redesign) as done — all were previously implemented but left unchecked.
- **Tests**: 19 new unit tests in `SeasonalContentManagerTest` covering all season windows, boundary dates, and off-season null returns. Existing ViewModel tests updated for new constructor params.

## v6.12.0
- Round 20 audit — Wallhaven SafeSearch toggles, auto-wallpaper rotation constraints, in-session source diagnostics, NewPipe stream-leak re-verify
- **Privacy / control**: Settings → API Keys now exposes the long-orphaned `showNsfwContent` toggle as a real UI control, plus a new `showSketchyContent` toggle for Wallhaven's intermediate sketchy tier. Without an API key both opt-ins coerce back to SFW-only — Wallhaven would otherwise reject the request and leave the user with an empty grid. `computeWallhavenPurity` extracted as a pure helper with full 8-combo unit coverage
- **Battery / data hygiene**: Auto-wallpaper rotation gains three opt-in execution constraints — Charging only, Wi-Fi only (sets `NetworkType.UNMETERED`), and Device idle only. ViewModel re-schedules the WorkManager job on every toggle change so the running worker picks up new constraints without waiting for the next interval boundary. `buildAutoWallpaperConstraints` extracted as a pure helper for unit testing
- **Observability**: New `SourceMetrics` singleton tracks per-source request count, success ratio, last error, and rolling p50/p95 latency for the current session. Settings → Diagnostics surfaces a snapshot dialog with a Reset button. Initial hooks land in `WallpaperRepository.getWallhaven` and `FreesoundV2Repository.search`; pattern is documented for follow-up coverage of the remaining content sources. CancellationException intentionally excluded from failure stats (it's structured-concurrency teardown, not a source failure)
- **Maintenance**: NewPipe Extractor v0.24.8 stream lifecycle re-verified clean (no `InputStream` / `BufferedReader` without `.use { }`). Version pinned with a documenting comment in `build.gradle.kts` so future bumps trigger a re-audit
- **Tests**: 19 new unit tests (5 `WallhavenPurityTest`, 5 `AutoWallpaperConstraintsTest`, 9 `SourceMetricsTest`); 186/186 total green

## v6.11.0
- Round 19 audit — Freesound rate-limit resilience, smarter Material You accent fallback, cancellation rethrow sweep
- **Reliability**: New `RateLimitInterceptor` wraps the OkHttp client and bounds-retries Freesound v2 API on HTTP 429. Honors `Retry-After` (capped at 30 s ceiling so a pathological response can't stall the app), max 2 retries, 1.5 s default fallback when the header is missing or negative. Scoped to `freesound.org` only — Wallhaven / Reddit / Pexels / Pixabay / SoundCloud pass through unchanged. Previously a routine search past Freesound's 60 req/min limit would silently blank the Sounds tab
- **Theming**: `ColorExtractor` now exposes `bestAccentColor` — a saturation/lightness-gated fallback ladder (dominant → vibrantDark → vibrant → vibrantLight → mutedDark → muted → mutedLight → dominant). Cartoon, monochrome, or near-greyscale wallpapers no longer hand the widget a dim grey "accent" via `Palette.getDominantColor`. The widget reads the new `tint_accent` SP key with a graceful fallback to legacy `tint_vibrant_light` for palettes cached before the upgrade
- **Structured concurrency**: 5 catch sites now rethrow `CancellationException` — `WallpaperHistoryManager.record` (widget palette write + widget refresh), `WallpapersViewModel.loadRandom`, `VideoWallpapersViewModel.applyVideoWallpaper` yt-dlp branch, `AudioTrimmer.applyFadeViaFfmpeg`. Cancellation now tears down cleanly instead of being surfaced as a generic state error or a swallowed log line
- **Tests**: 16 new unit tests (7 for `RateLimitInterceptor`, 9 for `ColorAccentSelector`); 167/167 total green

## v6.10.0
- Round 18 audit — finalized writes, widget intent safety, editor download caps, startup concurrency
- **Reliability**: `SoundEditorViewModel.downloadToCache` now checks the return value of `tmpFile.renameTo(file)`. Previously a rename failure (cross-volume rename on some OEM scoped-cache dirs, stale target file, or SELinux) was silent — the editor then tried to open a file that wasn't there. Falls back to `copyRecursively` + delete before throwing
- **Intent safety**: Three remaining widget callbacks (`OpenFavoritesAction`, `OpenCurrentWallpaperAction`, `OpenAppAction`) now wrap `startActivity` in try/catch. A missing or disabled launch activity no longer crashes the widget host process
- **Structured concurrency**: `FreeVibeApp.evictStaleCaches` now rethrows `CancellationException` instead of swallowing it. This matched the already-corrected `warmCommunityIdentity` pattern; the full app-startup background block now uniformly respects cancellation
- **Bounds**: `WallpaperCropViewModel.load` and `WallpaperEditorViewModel.loadFromUrl` now cap buffered image downloads at 64 MB (Content-Length + streamed), matching `WallpaperApplier` / `DualWallpaperService` / `DownloadManager`. A hostile CDN URL can no longer OOM the crop/edit flow

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
