# Aura — Product Roadmap

> From "impressive tech demo" to "the best open-source device personalization app."
> Organized by impact. Each phase builds on the last.

---

## Implementation Pass - 2026-04-25 Diagnostics Follow-Up

- [x] Completed T-6 follow-up from the v6.12.0 continuation brief: SourceMetrics now covers the remaining core wallpaper sources (Discover aggregate, Reddit, Bing, Pixabay, Pexels, Wallhaven variants) and sound sources (Openverse fallback, Freesound v2, YouTube, SoundCloud, Audius, ccMixter).
- [x] Diagnostics polish: Settings > Diagnostics now observes live in-session source updates, keeps Reset in-place, and shows summary chips plus per-source health rows instead of a static one-time snapshot.
- [x] Test coverage: SourceMetrics now has focused tests for the reusable measurement wrapper and live update tick.
- [ ] Remaining candidate: decide whether source diagnostics should add persisted export/report sharing. Keep it deferred until user reports need post-session debugging.
- [ ] Still deferred: T-8 plugin/source ABI, T-9 GLWallpaperService migration, and T-10 Firebase RTDB to Firestore migration remain large follow-ups requiring separate scoping.

## Implementation Pass - 2026-04-25 Create From Music

- [x] Completed the P0 1.5 app-level entry: Sounds > Create from music now opens the system audio picker and routes the selected local file straight into SoundEditorScreen's waveform loader.
- [x] Product polish: the editor opens in Create Sound mode for local files, avoids stale selected remote sounds, and skips redundant local URI reloads after recomposition/configuration changes.
- [x] Ringtone trim polish: long clips now default to a 30s selection, and the editor surfaces 8-30s guidance without blocking notification/alarm use cases.
- [x] Emulator smoke: generated local WAV opens in Create Sound mode with waveform, trim timing, guidance, fade controls, and ringtone/notification/alarm actions visible.
- [ ] Remaining candidate: repeat create-from-music QA through the system picker on a physical device with real user music files.

## Implementation Pass - 2026-04-26 Sounds Tab Chrome

- [x] Completed the P0 2.1 header simplification slice: the Sounds tab now exposes Ringtones, Notifications, and Alarms as visible primary chips instead of hiding the core modes behind a source dropdown.
- [x] Product polish: YouTube, Community, and active Search are kept in a compact secondary menu; quality bias remains in the existing bottom sheet behind the search-row Refine button.
- [x] Follow-up completed in the Sound Discovery Carousel pass: collection discovery was restored as compact in-feed cards.

## Implementation Pass - 2026-04-26 Sound Discovery Carousel

- [x] Completed the remaining 2.1 discovery slice: Sounds now shows tab-aware collection cards for Ringtones, Notifications, and Alarms inside the feed, routing each card into focused search.
- [x] Product polish: long-press quick apply now keeps the sheet open while applying, shows disabled/busy state in-place, and surfaces the missing system-settings permission with a direct Grant action.
- [x] Comparable-product research applied: Paperize-style organized collections, Muzei-style source browsing clarity, and ringtone-maker preview/apply workflow emphasis were used as fit checks without copying external code.
- [x] Test coverage: SoundQualityTest now covers collection visibility and nonblank distinct discovery queries.
- [ ] Remaining candidate: true first-visible preview pre-buffering remains deferred under 2.2; YouTube preview URL resolution already happens during search, but ExoPlayer/HTTP cache prewarming needs separate lifecycle and battery scoping.

## Phase 1 — Content Foundation

*Problem: Aura is a search engine pretending to be a curated library. Fix the content first.*

### 1.1 Ship Bundled Content ("Aura Collection")
- Bundle 200-500 high-quality CC0/public domain sounds in the APK (or download-on-first-launch asset pack)
  - 100+ ringtones (8-30s): melodic, minimal, electronic, marimba, retro
  - 100+ notification sounds (1-5s): chimes, pings, dings, clicks, pops
  - 100+ alarm sounds (10-40s): gentle wake, buzzer, nature, musical
  - 50+ bonus sound effects
- Source from Freesound.org's top-rated CC0 sounds in bulk (batch download, curate, normalize volume)
- Store in Room DB as `ContentSource.LOCAL_BUNDLED`, always available offline
- "Aura Originals" badge on these sounds
- First-run experience shows these immediately — no loading, no API calls, instant content

### 1.2 Freesound API v2 Direct Integration
- Register client_id at freesound.org/apiv2/apply/ (free, instant)
- New `FreesoundV2Api` Retrofit interface:
  - `GET /apiv2/search/text/?query=...&filter=duration:[8 TO 30]&sort=rating_desc&fields=id,name,tags,description,duration,avg_rating,num_downloads,previews,license,username&token=CLIENT_ID`
  - Quality signals: `avg_rating`, `num_downloads`, `sort=rating_desc`
  - Preview URLs: `previews.preview-hq-mp3` (128kbps), `previews.preview-lq-mp3` (64kbps)
  - Duration filtering via Solr `filter` parameter (server-side, not client-side)
- Replace Openverse as primary source — Openverse strips quality signals, Freesound direct has them
- Keep Openverse as fallback for when Freesound rate limit is hit
- Source badge: "FS" (blue) for Freesound direct

### 1.3 SoundCloud CC-Licensed Tracks
- SoundCloud API v2: `GET /tracks?q=ringtone&filter.license=cc-by&filter.duration[from]=8000&filter.duration[to]=30000`
- OAuth2 client credentials (register app at soundcloud.com/you/apps)
- Returns `stream_url` for direct playback
- Many artists upload ringtone-length clips and loops
- Quality signals: `playback_count`, `likes_count`, `reposts_count`
- Source badge: "SC" (orange)

### 1.4 Drop Internet Archive
- Remove `SoundRepository` (IA), `InternetArchiveApi`, `IAAudioCacheDao`, `ia_audio_cache` table
- DB migration to drop the table
- Remove IA from `ContentSource` enum (keep for legacy favorites compat)
- Remove progressive streaming callbacks — all remaining sources return results directly
- Massive simplification of `loadSounds()` — no more semaphores, mutexes, streaming callbacks

### 1.5 Ringtone Maker from Device Music
- [x] "Create from Music" button on Sounds tab
- [x] File picker: `ActivityResultContracts.GetContent("audio/*")`
- [x] Load into SoundEditorScreen with waveform
- [x] Add ringtone-specific 8-30s trim guidance/defaults; existing editor already supports manual trim and fade controls
- [x] One-tap set as ringtone/notification/alarm
- This alone is the #1 feature on ringtone apps (Ringtone Maker has 100M+ downloads)

---

## Phase 2 — UX Overhaul

*Problem: Engineer's app, not user's app. Too many controls, not enough delight.*

### 2.1 Simplify the Sounds Tab
- [x] Remove: duration filter chips, sort chips, genre chips, mood chips from the persistent header
- [x] New layout: Search bar → Tab row (Ringtones/Notifications/Alarms) → Content
- [x] Move quality refinement into the existing bottom sheet behind the search-row Refine button; no persistent genre/mood/sort rows remain
- [x] Collections carousel stays — it now lives in feed content as tab-aware discovery cards, not persistent chrome
- Result: 2 rows of chrome instead of 4, content starts at 20% of screen instead of 45%

### 2.2 Instant Sound Preview
- Pre-buffer the first 5 visible sounds' preview URLs on load
- For YouTube sounds: resolve preview URL during search, not on tap
- Tap play → instant playback, zero spinner
- For Freesound v2: previews are direct URLs, no resolution needed
- For bundled content: local file, instant

### 2.3 One-Tap Apply Flow
- From the sound list, long-press → bottom sheet with: "Set as Ringtone", "Set as Notification", "Set as Alarm", "Download"
- Regular tap → still goes to detail screen
- Detail screen simplified: play button + waveform + apply buttons + similar. Remove the 160dp circle, remove redundant info chips.

### 2.4 Onboarding Personalization
- First launch: "What's your style?" picker
  - Category cards: Minimal, Nature, Electronic, Retro, Classical, Pop, Cinematic, Lo-Fi
  - Multi-select, stored in DataStore
- Personalizes: Discover feed wallpapers, default sound genre, collection ordering, Staff Picks queries
- Can be changed anytime in Settings

### 2.5 Seasonal/Contextual Content
- `SeasonalContentManager` checks current date
- December: Christmas ringtones, snow wallpapers, holiday collections
- October: Halloween sounds, spooky wallpapers
- Summer: Beach/ocean themes
- New Year: Countdown sounds, fireworks wallpapers
- Valentine's: Love songs, romantic wallpapers
- Shown as a special "Seasonal" collection card at the top of each tab
- Queries are date-driven, auto-rotate

### 2.6 Sound Detail Screen Redesign
- Top: Waveform (full-width, 80dp) with integrated play/pause
- Below: Sound name (large), source + duration + uploader (one line)
- Primary action: 3 large buttons side-by-side (Ringtone / Notification / Alarm)
- Secondary: Trim, Download, Share, Contact — collapsed into icon row
- "More Like This" at bottom
- Total: fits on one screen without scrolling for most sounds

---

## Phase 3 — AI & Generation

*Problem: All content is sourced externally. Let users create their own.*

### 3.1 AI Wallpaper Generation
- Stability AI API (free tier: 25 credits/month, ~25 generations)
  - `POST https://api.stability.ai/v2beta/stable-image/generate/core`
  - Body: `{ "prompt": "...", "output_format": "png", "aspect_ratio": "9:16" }`
- UI: Text field + style picker (Photographic, Anime, Digital Art, 3D, Pixel Art)
- "Generate" button → loading animation → result preview → set as wallpaper
- Store generated wallpapers in a "My Creations" collection
- BuildConfig key with free tier default, user can add their own key in Settings
- **This is the single biggest differentiator vs every other open-source wallpaper app**

### 3.2 AI Sound Generation (Stretch)
- Meta's AudioCraft / MusicGen (open-source, can run on-device for short clips)
- Or: Replicate API with MusicGen model (~$0.002/generation)
- "Describe your ringtone" → generates 10-20s audio clip
- Style options: Melodic, Electronic, Ambient, Retro 8-bit
- Lower priority than wallpaper generation but very differentiating

### 3.3 Parallax/Depth Wallpapers
- ML Kit's depth estimation (on-device, free) on any static wallpaper
- Split into foreground/background layers
- Gyroscope-reactive parallax movement in `WallpaperService`
- Toggle in wallpaper detail: "Enable Parallax"
- Also works with AI-generated wallpapers

---

## Phase 4 — Community & Social

*Problem: Content library doesn't grow. Users consume but don't contribute.*

### 4.1 User-Generated Sound Uploads
- Record from microphone or pick from device
- Upload to Firebase Storage, metadata to Firebase RTDB
- Required fields: name, category (ringtone/notification/alarm), tags
- Community voting (already exists) serves as moderation
- Sounds with negative votes auto-hidden
- Top-voted community sounds appear in "Community Picks" section

### 4.2 User-Generated Wallpaper Uploads
- Pick from gallery, crop to phone aspect ratio
- Upload to Firebase Storage (compressed, max 4MB)
- Tags, categories, color extraction (automatic via Palette API)
- Community voting for quality control
- "Community" tab in Wallpapers

### 4.3 Creator Profiles
- Optional: sign in with Google
- Profile page: your uploads, total votes received, favorites count
- "Follow" creators to see their new uploads
- Top creators leaderboard

### 4.4 Shareable Collections
- Create a collection → generate a share link / QR code
- Recipient can import the collection (sounds download on demand)
- "Import Collection" scanner in the app

---

## Phase 5 — Video Wallpaper Evolution

### 5.1 Gallery Video/GIF Support
- `ActivityResultContracts.GetContent("video/*")` and `("image/gif")`
- Copy to app storage, set via `VideoWallpaperService`
- Trim start/end for loop selection (reuse audio trimmer UX pattern with video)
- Fit/fill/crop controls before applying
- This is the most requested feature for video wallpaper apps

### 5.2 Video Loop Editor
- Visual timeline scrubber with frame thumbnails
- Select loop start/end points
- Preview the loop before applying
- Crop via FFmpeg (already bundled)
- Useful for YouTube videos with intros/outros

### 5.3 VFX Particle Overlays
- Overlay particle effects on any wallpaper (static or video):
  - Snow, Rain, Fire Embers, Fireflies, Stars/Twinkle, Sakura Petals, Leaves, Bubbles
- Canvas-based particle system in `WallpaperService`
- Configurable: density, speed, direction
- Multiple effects stackable
- Battery impact: minimal (simple Canvas draws, 30 FPS cap)

### 5.4 Touch-Reactive Effects
- Water ripple effect on touch (Canvas displacement)
- Sparkle/particle burst at touch point
- Configurable in Settings (off/subtle/strong)
- Override `onTouchEvent` in `WallpaperService.Engine`

### 5.5 Battery Dashboard
- Real-time battery usage from video wallpaper service
- FPS counter overlay (dev mode)
- Estimated battery impact per setting (FPS limit, resolution, effects)
- Auto-reduce quality when battery < 15%

---

## Phase 6 — Smart Features

### 6.1 Material You Color Preview
- Extract Monet tonal palettes from wallpaper using Palette API
- Show 5 tonal palette strips (Primary, Secondary, Tertiary, Neutral, Neutral Variant)
- Mini UI mockup showing how system chrome, buttons, nav bar would look
- Display on wallpaper detail screen below the image
- "Your theme colors" section with hex values

### 6.2 Dark/Light Mode Auto-Switch
- Two wallpaper slots: "Light mode wallpaper" + "Dark mode wallpaper"
- `UiModeManager` listener for system theme changes
- Auto-applies correct wallpaper when user toggles system dark mode
- Optional: auto-darken the light wallpaper with AMOLED crush instead of requiring two

### 6.3 Weather Effects Overlay
- Open-Meteo API (free, no key): current weather conditions
- Canvas particle system overlay in WallpaperService:
  - Rain: vertical streaks, Snow: floating particles, Fog: gradient overlay
  - Sun rays: diagonal beams, Thunderstorm: flash overlay + rain
- Update weather every 30 minutes via WorkManager
- Intensity matches real conditions
- Works on both static and video wallpapers

### 6.4 Time-of-Day Adaptive Tint
- `ColorMatrix` filter in `WallpaperService` based on hour
- Dawn: warm golden, Day: neutral, Golden hour: warm amber, Night: cool blue
- Calculate sunrise/sunset from lat/lon (SolarCalculator algorithm, no API)
- Toggle + intensity slider in Settings

### 6.5 Smart Crop with Object Detection
- ML Kit Object Detection (free, on-device)
- When cropping landscape to portrait, detect primary subject
- Auto-position crop rectangle to center on the subject
- Apply to both wallpaper and video crop screens

---

## Phase 7 — Polish & Infrastructure

### 7.1 Unified Audio Service
- Single `AudioPlaybackService` replacing ExoPlayer in ViewModel + MediaPlayer in SoundEditor
- Foreground notification with play/pause/skip controls
- MediaSession integration (shows on lock screen, responds to Bluetooth controls)
- Handles all preview playback, trim preview, and apply operations

### 7.2 Replace SelectedContentHolder
- Navigation arguments via SavedStateHandle for passing wallpaper/sound between screens
- Or: shared ViewModel scoped to the nav graph
- Remove the global mutable singleton

### 7.3 Favorites Sync
- Google Sign-in (optional)
- Firebase Firestore for favorites, applied history, collections
- Sync across devices
- Offline-first: Room as source of truth, Firestore as sync target
- Export/import as JSON backup (already partially exists)

### 7.4 Widgets
- **Daily Wallpaper Widget** (Glance): shows today's wallpaper, tap to apply
- **Sound Quick-Set Widget**: 4 buttons (current ringtone name, tap to cycle through favorites)
- **Scheduler Controls Widget**: play/pause/skip for wallpaper rotation
- Multiple widget sizes (2x2, 4x2, 4x4)

### 7.5 Performance & Offline
- **Offline mode**: bundled content + cached favorites always available
- **Prefetch**: on WiFi, download next 10 wallpapers from scheduler source
- **Image cache**: Coil disk cache tuned for wallpaper sizes (currently uses defaults)
- **Startup time**: measure and optimize cold start to < 1.5s

---

## Phase 8 — Stretch Goals

### 8.1 Wallpaper Sets/Theming
- "Setup" concept: wallpaper + icon pack recommendation + widget suggestion
- Extract dominant colors from wallpaper → suggest matching icon packs from popular packs
- Share setups as links/QR codes

### 8.2 Watch Face Wallpapers (Wear OS)
- Companion Wear OS app
- Apply current wallpaper as watch face background
- Sync favorites to watch

### 8.3 Desktop Companion
- Electron/Tauri app or browser extension
- Apply Aura wallpapers to Windows/macOS/Linux desktop
- Sync favorites across phone + desktop

### 8.4 Stickers & Emoji
- Klipy API integration (already in codebase for GIFs)
- WhatsApp/Telegram sticker pack creation from search results
- Sticker tab in bottom nav (or merged with existing)

---

## Priority Matrix

| Priority | Item | Impact | Effort |
|----------|------|--------|--------|
| **P0** | 1.1 Bundled Content | Transforms first-run experience | Medium |
| **P0** | 1.4 Drop Internet Archive | Removes complexity, speeds up loads | Small |
| **P0** | 1.5 Ringtone Maker from Device Music | #1 requested feature category-wide | Medium |
| **P0** | 2.1 Simplify Sounds Tab UX | Biggest UX improvement | Small |
| **P1** | 1.2 Freesound API v2 | 10x better sound quality signals | Medium |
| **P1** | 2.2 Instant Sound Preview | Makes the app feel professional | Medium |
| **P1** | 2.3 One-Tap Apply Flow | Reduces time-to-value from 30s to 5s | Small |
| **P1** | 2.6 Sound Detail Redesign | Removes friction from core flow | Small |
| **P1** | 3.1 AI Wallpaper Generation | Biggest differentiator vs all competitors | Medium |
| **P1** | 5.1 Gallery Video/GIF Support | Most requested video wallpaper feature | Medium |
| **P2** | 1.3 SoundCloud Integration | New high-quality sound source | Medium |
| **P2** | 2.4 Onboarding Personalization | Better retention | Medium |
| **P2** | 2.5 Seasonal Content | Makes app feel alive | Small |
| **P2** | 3.3 Parallax/Depth Wallpapers | Premium feel | Large |
| **P2** | 4.1 User Sound Uploads | Grows content library organically | Large |
| **P2** | 5.2 Video Loop Editor | Table stakes for video wallpaper apps | Medium |
| **P2** | 5.3 VFX Particle Overlays | Eye candy, popular with GRUBL users | Medium |
| **P2** | 7.1 Unified Audio Service | Technical debt reduction | Medium |
| **P3** | 3.2 AI Sound Generation | Future differentiator | Large |
| **P3** | 4.2 User Wallpaper Uploads | Community growth | Large |
| **P3** | 4.3 Creator Profiles | Engagement | Large |
| **P3** | 5.4 Touch-Reactive Effects | Nice-to-have | Medium |
| **P3** | 6.1-6.5 Smart Features | Already partially implemented | Varies |
| **P3** | 7.2-7.5 Infrastructure | Technical health | Varies |
| **P4** | 8.1-8.4 Stretch Goals | Future vision | Large |

## Open-Source Research (Round 2)

### Related OSS Projects
- **Paperize (Anthonyy232/Paperize)** — https://github.com/Anthonyy232/Paperize — fully-offline dynamic wallpaper changer; Kotlin/Compose/Material 3 + MVVM; rotation schedules
- **WallFlow (ammargitham/WallFlow)** — https://github.com/ammargitham/WallFlow — GPLv3 Compose wallpaper app; multi-source (Wallhaven, Reddit), favorites, auto-change; plans KMP Windows support
- **WallCraft (Rahul-999-alpha/WallCraft)** — https://github.com/Rahul-999-alpha/WallCraft — AI-generated wallpapers (Stability AI/SDXL), Pexels+Unsplash sources, multi-res download (480p to 4K), AMOLED theme, WorkManager auto-changer
- **WallPap (hamzaazizofficial/WallPap)** — https://github.com/hamzaazizofficial/WallPap — Firebase backend, custom editor, saturation/opacity adjust, Google Drive save
- **Wallum** — https://github.com/Ink-sumo/wallum — lightweight Compose wallpaper; Retrofit/Hilt/Paging3
- **Darklify** — https://github.com/tasshack/darklify — dark/light dynamic switch based on system theme; Compose + Material 3
- **Livewallpaper samples (android-live-wallpaper topic)** — https://github.com/topics/android-live-wallpaper — GLSurfaceView/OpenGL ES reference implementations
- **Muzei** — https://github.com/muzei/muzei — classic wallpaper ecosystem with plugin "sources" — inspiration for an open Aura plugin API

### Features to Borrow
- Multi-source feed composition (Wallhaven + Reddit + Pexels + Unsplash) with per-source API key and SafeSearch toggles (WallFlow, WallCraft)
- AI-generated wallpaper tab using on-device Stable Diffusion XL or server-side proxy (WallCraft)
- WorkManager-backed auto-rotation with per-album schedules and charge-only / Wi-Fi-only constraints (Paperize, WallCraft)
- Custom editor: crop, saturation, opacity, blur, brightness before apply (WallPap)
- Google Drive / local-folder save-target option in addition to DCIM (WallPap)
- Plugin/source API so third parties can add sources without forking (Muzei sources)
- "Set on home / lock / both" three-way switch with per-target image choice (every modern Compose app)
- Darklify-style automatic day/night variant pairs (Darklify)
- Kotlin Multiplatform compose target for Windows live-paper app sharing (WallFlow KMP plan)
- Paging 3 + Coil caching for infinite scroll with thumbnail disk cache ceiling (Wallum)
- Favorites with cloud-backup-optional via Drive/Dropbox (WallCraft)

### Patterns & Architectures Worth Studying
- Muzei's "sources" plugin API — IPC-based, versioned ABI, each source a separate APK — lets community add wallpaper providers without shipping their keys (Muzei)
- WorkManager + periodic task for wallpaper rotation with constraint batching to preserve battery (Paperize)
- Clean MVVM + Repository + UseCase stack with StateFlow as single source-of-truth (Paperize)
- Per-source API key stored in EncryptedSharedPreferences — avoids shipping keys in the APK (WallCraft)
- Compose Material 3 dynamic color theming derived from the currently-set wallpaper's palette (general Material You reference)

## Implementation Deep Dive (Round 3)

### Reference Implementations to Study
- **rahulshah456/LiveSlider/app/src/main/java/com/rahulshah/liveslider/LiveWallpaperRenderer.kt** — https://github.com/rahulshah456/LiveSlider — OpenGL ES parallax renderer applying calculated X/Y offsets to texture coords from `WallpaperService.Engine.onOffsetsChanged`. Direct reference for replacing the current Canvas-based `ParallaxWallpaperService`.
- **GLWallpaperService/GLWallpaperService** — https://github.com/GLWallpaperService/GLWallpaperService — Apache-2.0 `GLEngine` base class that wires EGL context + rendering thread + WallpaperService lifecycle. Drop in as `app/src/main/java/com/freevibe/wallpaper/gl/`.
- **shubham0204/MLKit_Selfie_Segmentation_Android/app/src/main/java/com/ml/quaterion/facenetdetection/model/FrameAnalyser.kt** — https://github.com/shubham0204/MLKit_Selfie_Segmentation_Android — STREAM_MODE segmenter with proper close-on-destroy. Template for fixing segmenter lifecycle in `ParallaxWallpaperService` (already noted in v5.9.0 but single-source-of-truth reference).
- **Anthonyy232/Paperize/app/src/main/java/com/anthonyla/paperize/feature/wallpaper/wallpaper_service/WallpaperService.kt** — https://github.com/Anthonyy232/Paperize — WorkManager + constraint-batched rotation reference; compare against `AutoWallpaperWorker.kt` to spot missing constraints (charging-only, Wi-Fi-only).
- **ammargitham/WallFlow/app/src/main/java/com/ammar/wallflow/data/network/retrofit/wallhaven/RetrofitWallhavenNetwork.kt** — https://github.com/ammargitham/WallFlow — Wallhaven + Reddit multi-source Retrofit adapters with Paging 3; cleaner than Aura's per-source repository split.
- **muzei/muzei/muzei-api/src/main/java/com/google/android/apps/muzei/api/provider/MuzeiArtProvider.java** — https://github.com/muzei/muzei/blob/main/muzei-api/src/main/java/com/google/android/apps/muzei/api/provider/MuzeiArtProvider.java — IPC-based plugin-source API for third-party wallpaper providers. Template for a future Aura plugin ABI.
- **google/oboe/samples/LiveEffect/src/main/cpp/LiveEffect.cpp** — https://github.com/google/oboe/blob/main/samples/LiveEffect/src/main/cpp/LiveEffect.cpp — low-latency Oboe audio callback, useful if Aura upgrades the sound preview loop from ExoPlayer single-instance to Oboe for ringtone A/B previews.
- **airbnb/lottie-android/lottie/src/main/java/com/airbnb/lottie/LottieAnimationView.java** — https://github.com/airbnb/lottie-android — animated-wallpaper-template path via `LottieDrawable.draw(Canvas)` into a `WallpaperService.Engine` surface. Lets Aura ship "live wallpaper templates" without bundling per-template APKs.

### Known Pitfalls from Similar Projects
- **`SurfaceView` RGB_565 vs `WallpaperService` RGBX_8888 default pixel format** — Learn OpenGL ES article — switching `GLSurfaceView` into a live wallpaper without calling `setEGLConfigChooser(8,8,8,0,0,0)` produces banding. https://www.learnopengles.com/how-to-use-opengl-es-2-in-an-android-live-wallpaper/
- **ML Kit Selfie Segmenter adds ~4.5MB but `libxeno_native.so` can spike +50MB** — googlesamples/mlkit#386 — check APK size post-dependency bump. https://github.com/googlesamples/mlkit/issues/386
- **Selfie segmenter <10 fps on low-end at 360p** — googlesamples/mlkit#436 — always pre-downsample to 256×256 in STREAM_MODE before feeding `InputImage.fromBitmap`. https://github.com/googlesamples/mlkit/issues/436
- **ML Kit face-detector memory-leak class of bug** — googlesamples/mlkit#137 — if a new frame lands while detector is running, drop it; do not queue. Aura already does this for wallpapers but re-verify for sound waveforms. https://github.com/googlesamples/mlkit/issues/137
- **NewPipe-extractor API keeps breaking on YouTube backend changes** — NewPipe maintainers ship patches monthly; Aura must track the extractor version (not the app) and bump on every release. https://github.com/TeamNewPipe/NewPipeExtractor
- **Freesound v2 API token-bucket rate limiter — 60 req/min per IP** — Aura must back off with `Retry-After` handling; currently silent. https://freesound.org/docs/api/overview.html#rate-limiting
- **FFmpeg `libffmpeg.so` loaded via reflection from yt-dlp library** — fragile; on yt-dlp library upgrade the JNI symbol path can shift. Pin yt-dlp version + add a startup self-test that runs `ffmpeg -version` and disables video wallpapers if it fails.
- **Muzei plugin IPC requires API-versioning — breaking changes are silent** — Muzei 1.x → 2.x broke every third-party source. If Aura ships a plugin ABI, version it from day one. https://github.com/muzei/muzei/wiki/Changelog
- **Palette API colors unreliable on cartoon/solid-color images** — single dominant color from Palette can read as dark gray. Use `getDominantSwatch()` → fall back to `getVibrantSwatch()` → `getMutedSwatch()` before using for Material You accent. https://developer.android.com/reference/androidx/palette/graphics/Palette
- **Firebase Realtime Database quota on free tier (100 concurrent, 10GB/mo transfer)** — VoteRepository already uses ConcurrentHashMap, but a viral wallpaper can saturate quota. Shard community votes by hash or migrate to Firestore for better quota model.

### Library Integration Checklist
- **GLWallpaperService (OpenGL live wallpapers)** — no Maven coords; vendor `GLWallpaperService.java` + `GLEngine` + `RenderThread` into `com.freevibe.wallpaper.gl/`. Entry: `class ParallaxGlService : GLWallpaperService() { override onCreateEngine() = GLEngine().also { it.setEGLContextClientVersion(2); it.setRenderer(ParallaxRenderer()) } }`. Gotcha: `WallpaperService.Engine.onVisibilityChanged(false)` must pause the GLSurfaceView's render thread or battery drain doubles.
- **NewPipeExtractor (YouTube search + stream resolve)** — `com.github.TeamNewPipe:NewPipeExtractor:0.24.x` (pin to exact version, breaking changes every ~6 weeks). Entry: `NewPipe.init(DownloaderImpl.init(null)); val info = StreamInfo.getInfo(url)`. Gotcha: `DownloaderImpl` ships a buggy stream leak — use `InputStream.use { }` explicitly and wrap `BufferedReader` in `use` (Aura already fixed this in v5.8 but re-verify on every upstream bump).
- **ML Kit Selfie Segmentation** — `com.google.mlkit:segmentation-selfie:16.0.0-beta6` — entry: `Segmentation.getClient(SelfieSegmenterOptions.Builder().setDetectorMode(STREAM_MODE).enableRawSizeMask().build())`. Gotcha: `Segmenter.close()` must be called on `WallpaperService.Engine.onDestroy()` and the outstanding `Task` must complete or be observed; dropping it leaks ~7MB per engine lifecycle.


## Implementation Deep Dive (Round 3)

### Reference Implementations to Study
- **Freesound APIv2 docs** — https://freesound.org/docs/api/resources_apiv2.html — canonical endpoint reference; `GET /apiv2/search/text/` with `filter=license:"Creative Commons 0" duration:[1.0 TO 30.0]` and `sort=rating_desc` is the right shape for each content category
- **MTG/freesound-python** — https://github.com/MTG/freesound-python — official Freesound reference client (Python); port the field masks, auth header (`Authorization: Token <key>`), and pagination cursor to the Kotlin/Retrofit layer
- **AlynxZhou/alynx-live-wallpaper** — https://github.com/AlynxZhou/alynx-live-wallpaper — the cleanest ExoPlayer + OpenGL ES center-crop video wallpaper reference; adopt its `GLWallpaperService` engine wholesale for video wallpaper support
- **cyunrei/Video-Live-Wallpaper** — https://github.com/cyunrei/Video-Live-Wallpaper — minimal-memory variant, good for low-end device path
- **google/ExoPlayer / androidx.media3** — https://github.com/androidx/media — current home of Media3; the roadmap's "preview player" should track media3 1.9.x like NovaCut
- **Android Ringtone API reference** — https://developer.android.com/reference/android/media/RingtoneManager — `RingtoneManager.setActualDefaultRingtoneUri` + `MediaStore.Audio.Media.IS_RINGTONE` metadata; the only approved path to set system ringtone on API 26+
- **opensource.creativecommons.org Freesound intro** — https://opensource.creativecommons.org/blog/entries/freesound-intro/ — license compliance walkthrough, CC0 redistribution rules for the bundled "Aura Collection"
- **Openverse** — https://api.openverse.engineering — the source the roadmap calls out as losing quality signals; keep as secondary fallback, not primary

### Known Pitfalls from Similar Projects
- **Freesound `/apiv1/search/text/` deprecated Nov 2025** — https://freesound.org/docs/api/resources_apiv2.html — v1 endpoints redirect but will eventually 410; hard-code v2 paths from day one
- **API token vs OAuth2** — https://freesound.org/help/developers/ — download of full (non-preview) audio requires OAuth2, not the simple API token; the token only gets you metadata + 128kbps previews
- **Previews are MP3, not original** — Freesound API ref — `previews.preview-hq-mp3` is re-encoded and may not match the original's loudness; normalize bundled originals separately
- **MediaStore ringtone write permission** — https://developer.android.com/training/data-storage/shared/media — on Android 11+ `WRITE_EXTERNAL_STORAGE` no longer works; must use `MediaStore.createPendingInsert` + `Ringtone` URI flip — many OSS apps still fail silently here
- **ExoPlayer holds audio focus by default** — AlynxZhou README above — must call `.setAudioAttributes(null, false)` or live wallpaper steals audio from music apps
- **H.264 wallpaper burns battery** — XDA analysis linked above — prefer MPEG-4/DivX re-encode or cap 480p@20fps; H.265 is usually worse for wallpaper due to decode overhead not being offset by bitrate savings
- **`onVisibilityChanged(false)` must pause** — https://android-developers.googleblog.com/2010/02/live-wallpapers.html — apps that don't release the decoder on invisibility drain battery even when the user is in another app
- **CC0 does not protect against DMCA re-uploads** — community cautionary threads — verify each bundled file's uploader-claimed CC0 against Freesound's moderation flag before shipping; keep a sha256 manifest for retroactive removal

### Library Integration Checklist
- **Retrofit 2.11 + OkHttp 4.12** — `@GET("apiv2/search/text/") fun search(@Query("query") q: String, @Query("filter") f: String, @Query("fields") fs: String, @Query("page_size") ps: Int): Response<SearchResponse>`; header interceptor adds `Authorization: Token ${BuildConfig.FREESOUND_TOKEN}`; gotcha: `filter` must be URL-encoded incl. the quotes around license names
- **kotlinx.serialization 1.7** — for `SearchResponse` models; gotcha: Freesound fields can be null in unexpected places (`avg_rating=0` for unrated) — mark all rating fields nullable
- **androidx.media3 1.9.2** (ExoPlayer) — match NovaCut's pin; `ExoPlayer.Builder(context).setAudioAttributes(AudioAttributes.DEFAULT, false).build()`; gotcha: handle `Player.STATE_ENDED` by re-seeking to 0 for looping wallpapers — `setRepeatMode(REPEAT_MODE_ONE)` is cheaper than manual seek
- **OpenGL ES 2.0 renderer** for video wallpaper — port from alynx-live-wallpaper; shader program with `SurfaceTexture` → fragment shader `samplerExternalOES`; gotcha: Samsung devices re-create the GL context when wallpaper picker previews, handle `onSurfaceCreated` idempotently
- **androidx.work 2.10** — for "Aura Originals" asset-pack download on first launch; `OneTimeWorkRequest` with network constraint; gotcha: large blob downloads need `setExpedited(OUT_OF_QUOTA_POLICY.RUN_AS_NON_EXPEDITED_WORK_REQUEST)`
- **androidx.datastore-preferences 1.1** — for token + user prefs; do NOT store Freesound OAuth refresh token in SharedPreferences (plaintext); use EncryptedSharedPreferences with MasterKey
- **coil 2.7** (not Glide) — for artwork thumbnails in the browser grid; already AMOLED-friendly, matches FreeVibe/Aura's existing Compose stack
- **Room 2.7** — `ContentSource.LOCAL_BUNDLED` enum value in the existing sound DB; schema migration needs an `is_bundled` flag + `sha256` column for the retroactive-removal path
- **Room FTS4 virtual table** — for tag search; gotcha: FTS doesn't like the `-` in `Creative-Commons-0`; tokenize on spaces and strip punctuation in the insert trigger
