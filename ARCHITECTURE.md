# Aura — Architecture

Living architecture overview for contributors. Internal working notes live in `CLAUDE.md` (gitignored). This file describes the layered model + extension points.

## Layered model

```
┌────────────────────────────────────────────────────────────────────┐
│  Compose UI                                                        │
│   16+ screens, 5 bottom-nav tabs (Wallpapers / Videos / Sounds /   │
│   Favorites / Settings) + Editors / Collections / Downloads /      │
│   Onboarding. Material 3 + custom rectangular 4-12dp shape system. │
├────────────────────────────────────────────────────────────────────┤
│  ViewModels (Hilt @HiltViewModel)                                  │
│   One per screen surface. Expose StateFlow<UiState>. Bridge to     │
│   repositories + services. Lifecycle-scoped coroutines via         │
│   viewModelScope. SelectedContentHolder singleton bridges          │
│   selection across NavBackStackEntries (persists to disk for       │
│   process-death survival — NX-4).                                  │
├────────────────────────────────────────────────────────────────────┤
│  Repositories (data/repository/)                                   │
│   One per source: Wallhaven / Pexels / Pixabay / Bing / Reddit /   │
│   YouTube / Freesound v2 (legacy) / SoundCloud (legacy) / Audius   │
│   (legacy) / ccMixter (legacy) / AI / Collection / Vote / Upload / │
│   CreatorProfile / Favorites / SearchHistory / WallpaperUpload.    │
│   Aggregator: WallpaperRepository.getDiscover() mixes feeds with   │
│   per-source timeouts.                                             │
├────────────────────────────────────────────────────────────────────┤
│  Network (data/remote/)                                            │
│   Retrofit interfaces + Moshi + OkHttp. RateLimitInterceptor on    │
│   Freesound. SourceMetrics singleton wraps every fetch with p50/p95│
│   latency + success-ratio rolling counters (Settings → Diagnostics)│
├────────────────────────────────────────────────────────────────────┤
│  Local (data/local/)                                               │
│   Room DB v14 (favorites, downloads, search_history, wallpaper_    │
│   cache, wallpaper_history, wallpaper_collections,                 │
│   wallpaper_collection_items). DataStore: Settings + Onboarding +  │
│   User Styles + Rotation triggers. PreferencesManager is the only  │
│   reader/writer.                                                   │
├────────────────────────────────────────────────────────────────────┤
│  Services (service/)                                               │
│   WallpaperService implementations:                                │
│     VideoWallpaperService (MediaPlayer + Canvas GIF renderer)      │
│     ParallaxWallpaperService (ML Kit subject segmentation +        │
│       accelerometer parallax — see N-3 / NX-3)                     │
│     WeatherWallpaperService (Open-Meteo + Canvas particle overlay) │
│     DualWallpaperService (separate home/lock slots)                │
│   Foreground services:                                             │
│     AudioPlaybackService (Media3 MediaSession + AudioPreviewCache) │
│     BatchDownloadService (cancellable batch favorite downloads)    │
│     RotationTriggerService (NX-6: USER_PRESENT + SCREEN_OFF)       │
│   Workers (CoroutineWorker + Hilt):                                │
│     AutoWallpaperWorker (periodic 15-min+ rotation)                │
│     DailyWallpaperWorker (daily pick refresh)                      │
│     WeatherUpdateWorker (30-min weather poll)                      │
│     AuraOriginalsDownloader (N-5: CC0 sound pack download)         │
│   One-shot services:                                               │
│     WallpaperApplier (applyByLocator: http/file/content/path)      │
│     SoundApplier (RingtoneManager + MediaStore)                    │
│     DownloadManager (StateFlow-driven download queue)              │
│     AudioTrimmer (MediaMuxer + FFmpeg fade/normalize/convert)      │
│     ContactRingtoneService (per-contact ringtone assignment)       │
│     SmartCropDetector (NX-3: ML Kit subject bbox)                  │
│     SmartCropCalculator (pure geometry, unit-tested)               │
│     ColorExtractor (Palette + ColorAccentSelector)                 │
│     SourceMetrics (in-session p50/p95 per source)                  │
│   Receivers:                                                       │
│     FreeVibeWidgetReceiver (Glance widget)                         │
│     TaskerActionReceiver (L-2: ROTATE_NOW + SHUFFLE_NOW)           │
├────────────────────────────────────────────────────────────────────┤
│  External                                                          │
│   YouTube: NewPipe Extractor + yt-dlp (no API key)                 │
│   FFmpeg: bundled via yt-dlp Python, accessed via reflection on    │
│     youtubedl-android's static fields                              │
│   Firebase: RTDB (community voting) + Storage (uploads) + Auth     │
│     (anonymous default; optional Google sign-in queued)            │
│   ML Kit: Subject Segmentation (unbundled, Google Play services)   │
│   Stability AI: AI wallpaper generation (bring-your-own-key)       │
│   Open-Meteo: free weather, no key, no rate limit                  │
└────────────────────────────────────────────────────────────────────┘
```

## Package map

```
app/src/main/java/com/freevibe/
├── FreeVibeApp.kt                 # Application + Coil image loader + crash logging
├── MainActivity.kt                # Single-activity host
├── data/
│   ├── local/                     # Room + DataStore
│   ├── model/                     # Wallpaper, Sound, FavoriteEntity, …
│   ├── paging/                    # PagingSources
│   ├── remote/                    # Retrofit + RateLimitInterceptor + SourceMetrics hooks
│   └── repository/                # one per source + aggregators
├── di/
│   └── AppModule.kt               # Hilt bindings: OkHttp, Retrofit, Room, Moshi
├── service/                       # See "Services" layer above
├── ui/
│   ├── FreeVibeRoot.kt            # NavHost + bottom nav + widget deep linking
│   ├── components/                # SharedComponents (AuraStateCard, GlassCard, …)
│   ├── navigation/                # NavGraph definitions
│   ├── screens/                   # one per feature surface
│   └── theme/                     # Material 3 + Aura design tokens
└── widget/
    └── FreeVibeWidget.kt          # Glance home + lockscreen widget (NX-2)
```

## Key abstractions

### `WallpaperApplier.applyByLocator(uri, target)`

Scheme-dispatching wallpaper-set entry point. Routes:
- `http(s)://` → OkHttp + bounded `readCapped` (64 MB ceiling) → `BitmapFactory.decodeByteArray`
- `file://` / absolute path → `BitmapFactory.decodeFile` with `inSampleSize`
- `content://` → `ContentResolver.openInputStream` + `copyCapped`

Use this for any new "apply a wallpaper" path. The HTTP-only `applyFromUrl` is legacy — it crashes on `file://` and `content://` schemes.

### `SelectedContentHolder`

Singleton bridging detail screens with the list ViewModel they came from. Holds:
- `selectedWallpaper: StateFlow<Wallpaper?>` — persisted to SharedPreferences JSON (NX-4)
- `wallpaperList: StateFlow<List<Wallpaper>>` — in-memory only; detail-screen pager source
- `selectedSound: StateFlow<Sound?>` — persisted

Full removal is queued behind Navigation 2.9 type-safe routes (NX-4 full sweep, N-1-gated).

### `AutoWallpaperWorker`

Single rotation worker handling both the legacy and enhanced scheduler paths. Reads:
- `prefs.schedulerEnabled` → `doSchedulerWork()` (home/lock split, collections, day/night)
- `prefs.autoWallpaperEnabled` → `doLegacyWork()`

Enqueued in three ways:
- `WorkManager.enqueueUniquePeriodicWork` — periodic 15-min+ rotation (`AutoWallpaperWorker.schedule`)
- `RotationTriggerService.enqueueRotation()` — one-shot expedited on USER_PRESENT / SCREEN_OFF (NX-6)
- `TaskerActionReceiver` → `enqueueRotation()` — broadcast-driven (L-2)

### `SmartCropDetector` + `SmartCropCalculator`

Reusable across wallpaper crop, video crop, and any future surface that needs subject-aware framing. The detector wraps ML Kit Subject Segmentation; the calculator is pure geometry (unit-tested). Different viewport conventions (wallpaper absolute scale vs. video relative-to-fit) are reconciled in the call site, not in the helper.

### `WallpaperHistoryManager`

Records every applied wallpaper with extracted Palette colours. Drives:
- Home/lock widget background tint
- Material You accent (Settings → Theme)
- "On this day" recall (queued, L-7)

## Process-death + lifecycle

- ViewModels: scoped to `NavBackStackEntry`. Survive config changes; recreated after process death.
- `SelectedContentHolder`: persists primary selection to disk on every `select*` call; restores on Hilt construction. Pager list is in-memory only; detail screen collapses to single-item display when list is empty.
- `WallpaperService` engines: pause render thread on `onVisibilityChanged(false)`. Sensors deregister on invisible. FFmpeg subprocesses survive coroutine cancellation (process-scoped, not coroutine-scoped) — caller must handle via UI guard (see NX-13 VideoCropScreen back-press toast).

## Live-wallpaper engine discipline

Every live wallpaper must:

1. Stop rendering when `onVisibilityChanged(false)`.
2. Cap FPS to 30 by default; cap to 15 when battery < 15 % AND not charging.
3. Use SurfaceView (not TextureView) — 30 % less battery per the existing benchmark.
4. Synchronize bitmap access (parallax engine writes from segmenter callback, reads from draw loop).
5. Recycle bitmap layers in `onDestroy` and on engine teardown.

`VideoWallpaperService` and `ParallaxWallpaperService` are the reference implementations. New engines should follow the same lifecycle protocol — see ROADMAP NX-1 (GL/AGSL engine migration) for the planned consolidation.

## Build and CI

- `assembleDebug` / `testDebugUnitTest` / `lintDebug` run on every push and PR via [`verify.yml`](.github/workflows/verify.yml).
- `assembleRelease` runs on `v*` tag push via [`release.yml`](.github/workflows/release.yml), signing with `freevibe.jks`.
- Per-ABI APK splits + F-Droid reproducible-build verification are queued (NX-8).

## Design system

- AMOLED-first dark theme; neutral surfaces; brass / mist / coral accents.
- Rectangular 4-12dp radii. **No pill / oval / fully-rounded backdrops** — see CLAUDE.md and the v6.16.0 changelog for the rule.
- Zero letter-spacing; calmer elevation; status differentiated by colour / border / font weight, not shape.
- Shared components: `GlassCard`, `HighlightPill`, `CountBadge`, `AuraStateCard`, `CompactSearchField`.

## Roadmap

[`ROADMAP.md`](ROADMAP.md) is the source of truth for what's queued. Every item links to sources in the Appendix. Items shipped in the Implementation Log are dated and traceable.

## Related docs

- [`CONTRIBUTING.md`](CONTRIBUTING.md) — how to open a PR, code style, charter.
- [`ROADMAP.md`](ROADMAP.md) — tiered backlog with scoring + sources.
- [`CHANGELOG.md`](CHANGELOG.md) — release notes.
- [`docs/firebase-admin-claims.md`](docs/firebase-admin-claims.md) — Firebase Custom Claims runbook (N-2).
- [`docs/aura-originals-curation.md`](docs/aura-originals-curation.md) — Aura Originals CC0 curation workflow (N-5).
