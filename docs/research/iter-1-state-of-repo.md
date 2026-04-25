# Iter 1 — State of Repo (Phase 0)

**Date:** 2026-04-24
**Repo:** Aura (FreeVibe / com.freevibe)
**Version at iter start:** v6.10.0 (versionCode 90)
**HEAD:** 536f7f1 — `docs(roadmap): append round 2-3 OSS research notes`

## Scale
- 211 tracked files, 32,186 Kotlin LOC, 33 test files
- 50 commits total in history; 18 prior audit rounds documented in CHANGELOG/CLAUDE.md
- ROADMAP.md is dense narrative (Phases 1–8 + Round 2 OSS research + Round 3 deep-dive); no checkbox-style trackers
- Below all Large-Repo-Mode thresholds → standard single-iteration loop

## Charter (extracted from CLAUDE.md / README)
- "Open-source Android app for device personalization"
- 20+ content sources: wallpapers, video wallpapers, sounds
- Weather effects, AMOLED editor, smart scheduler, Material You preview
- Community voting via Firebase, curated discovery, FFmpeg video crop
- Dark-first (AMOLED), minSdk 26, target 35, Kotlin 2.1.0, JDK 17
- Tech pin: Hilt 2.53.1, Room 2.6.1 v14, Retrofit 2.11, Coil 2.7, Media3, WorkManager 2.10, Glance 1.1.1

Charter takeaway: Aura is a *content + personalization* app, not a content-creation tool. Reject ROADMAP items that drift toward AI-generation tooling beyond what's already proven (parallax, weather effects). Plugin / community work is on-charter; AI sound generation is out-of-charter (already pruned in v5.0.0).

## Recent audit lineage (rounds 14–18, v6.6.0–v6.10.0)
- Round 14: bitmap leaks, segmenter double-close, FFmpeg readText bounded
- Round 15: download-byte caps (32 MB ColorExtractor, 64 MB SoundApplier, 64 MB DualWallpaper, 64 MB WallpaperApplier), Locale.ROOT sweep, intent safety
- Round 16: video crop hardening, offline-cache bounds, prefs write-order
- Round 17: more last-mile download caps
- Round 18 (v6.10.0): finalized writes, widget intent safety, editor download caps (64 MB cap on crop / editor), CancellationException rethrow in evictStaleCaches

Pattern: caps + cancellation rethrow + Locale.ROOT have been ground over thoroughly. Diminishing returns there.

## Verified open issues against current code

### F-1: Freesound 429 / Retry-After silent failure
- `FreesoundV2Repository.search()` calls `api.search()` directly. On HTTP 429 Retrofit throws `HttpException`, which propagates as a generic error to the UI.
- `AppModule.provideOkHttpClient()` has no retry / 429-aware interceptor.
- Roadmap (Round 3) explicitly notes this: "Freesound v2 API token-bucket rate limiter — 60 req/min per IP — Aura must back off with `Retry-After` handling; currently silent."
- Reproducer: open Sounds tab, page through results 60+ times in <1 minute. Search fails silently.

### F-2: Palette dominant-color fallback ladder missing
- `ColorExtractor.extractFromBitmap()` exposes all 7 swatches but `dominantColor` is consumed directly by Material You preview.
- Roadmap (Round 3) note: "Palette dominant can read as dark gray on cartoon/solid-color images. Use dominant → vibrant → muted ladder."
- No `accentColor` / `bestAccentColor` field exists; consumers must replicate the ladder themselves.

### F-3: NewPipe DownloaderImpl stream re-use re-verify
- Round 3 note: "Aura already fixed this in v5.8 but re-verify on every upstream bump."
- Last NewPipe-related work was v5.8.0 (Aug 2025 era per version pacing).
- YouTubeRepository / DownloaderImpl needs a stream-leak audit.

### F-4: ExoPlayer audio-focus on video wallpapers (NOT a current bug)
- `VideoWallpaperService` uses **MediaPlayer**, not ExoPlayer, and explicitly calls `setVolume(0f, 0f)`. Audio focus stealing is not happening today.
- Roadmap pitfall is forward-looking for Media3 migration. Park as note for the future GLWallpaperService work; do NOT fix preemptively.

### F-5: GLSurfaceView RGB_565 banding (NOT a current bug)
- No GLSurfaceView in the codebase yet. ParallaxWallpaperService uses Canvas + ML Kit. Park.

### F-6: Firebase RTDB quota saturation (speculative)
- Round 3 cited theoretical 100-concurrent / 10 GB cap risk.
- No production telemetry exists to confirm we're approaching the cap. Park as observability task, not a fix.

## Already-shipped items in ROADMAP (no work needed)
- 1.2 Freesound v2 direct integration: shipped (FreesoundV2Repository present)
- 1.3 SoundCloud CC-licensed: shipped (SoundCloudApi.kt + SoundCloudRepository.kt)
- 1.4 Drop Internet Archive: shipped (db migration v6→v7 dropped ia_audio_cache)
- 1.5 Ringtone Maker from Device Music: partial (SoundEditor exists for trim/fade)
- 6.1 Material You Color Preview: shipped (ColorExtractor present)
- 6.5 Smart Crop with Object Detection: partial (ML Kit segmentation in ParallaxWallpaperService)
- 7.1 Unified Audio Service: shipped (AudioPlaybackManager + Service)
