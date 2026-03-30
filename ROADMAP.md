# Aura — Product Roadmap

> From "impressive tech demo" to "the best open-source device personalization app."
> Organized by impact. Each phase builds on the last.

---

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
- "Create from Music" button on Sounds tab
- File picker: `ActivityResultContracts.GetContent("audio/*")`
- Load into SoundEditorScreen with waveform
- User trims to 8-30s, applies fade in/out
- One-tap set as ringtone/notification/alarm
- This alone is the #1 feature on ringtone apps (Ringtone Maker has 100M+ downloads)

---

## Phase 2 — UX Overhaul

*Problem: Engineer's app, not user's app. Too many controls, not enough delight.*

### 2.1 Simplify the Sounds Tab
- Remove: duration filter chips, sort chips, genre chips, mood chips from the persistent header
- New layout: Search bar → Tab row (Ringtones/Notifications/Alarms) → Content
- Move genres/moods/sort into a bottom sheet filter (filter icon button in search bar)
- Collections carousel stays — it's the primary discovery mechanism
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
