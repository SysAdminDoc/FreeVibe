# Aura — Product Roadmap

> Open-source Android personalization: wallpapers, video wallpapers, ringtones, sounds.
> Stay the OSS alternative to Zedge: no ads, no surprise charges, no dark patterns.

**Version:** 2026-05-16 (supersedes 2026-05-XX AI Wallpaper pass).
**Code version at write:** v6.31.0 / versionCode 111.
**Charter:** personalization, AMOLED-first, free-by-default, multi-source content aggregation, community-fed catalog, polite live wallpapers (battery-aware, pause-on-invisible).

---

## How to read this document

- **State of the Repo** — what's actually shipped, with receipts.
- **Now / Next / Later / Under Consideration / Rejected** — tiered backlog with one-line rationale per item. Tier = sum of fit/impact/effort/risk/dependencies/novelty, ≥24 = Now, 18–23 = Next, 12–17 = Later, <12 = Under Consideration. Charter conflict = Rejected.
- **Themes** — cross-cutting initiatives that span multiple items.
- **Risk Register** — known landmines for the next 12 months of execution.
- **Implementation Log** — preserved release-pass entries (the receipts for what shipped).
- **Appendix** — every cited URL, organized by class.

If you're adding a feature and the source isn't in the Appendix, do not add it. A roadmap without sources is a wishlist.

---

## State of the Repo (snapshot, 2026-05-16)

- Kotlin 2.1.0 / Compose / Material 3, Hilt 2.53.1, Room 2.6.1 (v14), Retrofit 2.11.0, OkHttp 4.12.0, Media3 1.5.1, Coil 2.7.0, WorkManager 2.10.0, Glance 1.1.1, NewPipe Extractor 0.24.8, youtubedl-android 0.18.1, ML Kit `segmentation-selfie:16.0.0-beta6`, Firebase BoM 33.7.0.
- 121 Kotlin files, ~35k LOC, 46 unit-test files, 0 known CVEs in scanner, 0 production TODO comments (one design-note TODO in `VoteRepository.kt:75` for admin auth).
- Shipped via 21 Implementation Passes since 2026-04-25 (see Implementation Log).
- Distribution: GitHub Releases only; signed via `freevibe.jks`. CI workflow `.github/workflows/release.yml` triggered on `v*` tag.
- Package id `com.freevibe`, brand "Aura"; do not change without a migration plan (re-installs lose data; existing community uploads keyed by device id).

### What is shipped (Phase 1-7 status)

Preserved verbatim from prior passes; do not edit unless a regression occurred.

#### Phase 1 — Content Foundation
- [ ] **1.1 Aura Originals bundled content (Phase 1.1)** — still a promise, not a shipment. URL-backed cache exists; bundled CC0 sound pack does not. Carried to **Now** below.
- [x] 1.2 Freesound API v2 (`FreesoundV2Repository`, rating + duration filters, RateLimitInterceptor).
- [x] 1.3 SoundCloud CC (`SoundCloudApi.kt`, retained as legacy; not in active feed since v6.18.0).
- [x] 1.4 Internet Archive removed (DB migration v6→v7).
- [x] 1.5 Ringtone Maker from device music (Create from Music → SoundEditorScreen, ringtone-specific 8–30s trim defaults).

#### Phase 2 — UX Overhaul
- [x] 2.1 Sounds-tab simplification (Ringtones / Notifications / Alarms primary chips; YouTube / Community / Search in secondary menu; Refine in bottom sheet).
- [x] 2.2 Instant sound preview (first-5 prebuffer via Media3 SimpleCache; Ready badge on cards).
- [x] 2.3 QuickApplySheet (long-press → Ringtone / Notification / Alarm / Download).
- [x] 2.4 Onboarding style picker + Settings re-entry; style-biased Wallhaven + Pexels + Pixabay Discover.
- [x] 2.5 Seasonal content (`SeasonalContentManager`: Halloween / Holiday / New Year / Valentine / Summer).
- [x] 2.6 Sound Detail redesign (waveform top, three apply buttons, More Like This).

#### Phase 3 — AI & Generation
- [x] 3.1 AI Wallpaper Generation via Stability AI (server-side; `StabilityAiApi`, `AiWallpaperRepository`, 8 styles, 50-image cap, per-call PNG, BuildConfig+DataStore key handling).
- [ ] 3.2 AI Sound Generation — out of scope per v5.0.0 charter prune; revisit in **Under Consideration**.
- [x] 3.3 Parallax wallpapers (`ParallaxWallpaperService` + ML Kit Selfie Segmentation).

#### Phase 4 — Community
- [x] 4.1 User-generated sound uploads (Firebase Storage 20 MB cap, RTDB metadata, vote-based moderation, Community Picks).
- [x] 4.2 User-generated wallpaper uploads (gallery → crop → compressed JPEG ≤4 MB, Palette colors, RTDB).
- [x] 4.3 Creator profiles (anonymous Firebase identity; follow, top-creator leaderboard). Google sign-in still deferred — see Phase 7.3.
- [x] 4.4 Shareable collections (Aura links, QR codes, JSON files; `aura://collection/import/{token}` deep link).

#### Phase 5 — Video Wallpaper Evolution
- [x] 5.1 Local video/GIF import (`ActivityResultContracts.OpenDocument`, animated GIF canvas renderer, Fit/Fill scale mode).
- [x] 5.2 Loop & Crop editor (frame thumbnails, range scrubber, loop preview, FFmpeg `-ss`/`-t`).
- [x] 5.3 VFX particle overlays (`VfxParticleRenderer`: FIREFLIES / SAKURA / EMBERS / BUBBLES / LEAVES / SPARKLES).
- [x] 5.4 Touch-reactive effects (ripple + sparkle bursts, bounded, capped).
- [x] 5.5 Video battery dashboard (heartbeat, FPS, scale mode, auto 15 FPS cap below 15 % battery).

#### Phase 6 — Smart Features
- [x] 6.1 Material You color preview (5 tonal palettes on detail screen; `ColorAccentSelector` saturation/lightness gate ladder).
- [x] 6.2 Dark/light auto-switch (`SystemThemeListener` via `ComponentCallbacks.onConfigurationChanged`; per-slot wallpaper IDs; `WallpaperApplier.applyByLocator` handles file/content/http schemes).
- [x] 6.3 Weather effects overlay (`WeatherParticleRenderer` + `WeatherWallpaperService`, NOAA-coded effects, 30-min `WeatherUpdateWorker`).
- [x] 6.4 Time-of-day adaptive tint (`SolarCalculator` DST-aware, `ColorMatrix` cached per 5-min bucket).
- [ ] **6.5 Smart Crop with subject detection** — never shipped. Carried to **Next** below.

#### Phase 7 — Polish & Infra
- [x] 7.1 Unified Audio Service (`AudioPlaybackService` + MediaSession + `AudioPreviewCache`).
- [ ] **7.2 SelectedContentHolder replacement** — still a global singleton; brittle on process death. Carried to **Next**.
- [ ] **7.3 Favorites sync (Firestore + Google sign-in)** — blocked on `default_web_client_id` in `google-services.json`. Carried to **Next**.
- [ ] 7.4 Additional widgets (Daily Wallpaper, Sound Quick-Set, Scheduler Controls) — partial; shuffle widget exists. Carried to **Later**.
- [ ] 7.5 True offline mode + prefetch + < 1.5 s cold start — partial; Coil disk cache shipped, offline favorites manager exists. Carried to **Later**.

#### Phase 8 — Stretch (none shipped)
- 8.1 Wallpaper sets (wallpaper + icon-pack + widget bundle). **Later.**
- 8.2 Wear OS companion. **Later** (re-scoped for Wear OS 6 + Watch Face Push).
- 8.3 Desktop companion (Tauri/Electron). **Later.**
- 8.4 Stickers / emoji. **Under Consideration.**

---

## Now — execute this cycle

Five items. Each is dependency-coupled or unblocks the rest of the backlog. None are speculative.

### N-1. Toolchain upgrade triad (AGP 9 + Gradle 9 + Kotlin 2.3 + Compose BOM May 2026 + Hilt 2.59)

- **Source(s):** [AGP 9.0 release notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes); [AGP 9.1 notes](https://developer.android.com/build/releases/agp-9-1-0-release-notes); [Kotlin 2.3](https://kotlinlang.org/docs/whatsnew23.html); [Compose Apr-26 update](https://android-developers.googleblog.com/2026/04/jetpack-compose-april-2026-updates.html); [Dagger 2.59 release](https://github.com/google/dagger/releases/tag/dagger-2.59); [Compose Strong Skipping](https://developer.android.com/develop/ui/compose/performance/stability/strongskipping); [Android 17 SDK 37 adaptive requirement](https://developer.android.com/about/versions/17/release-notes).
- **Why now:** Hilt 2.59 requires AGP 9 + Gradle 9.1; AGP 9 makes Kotlin built-in; KSP1 is incompatible with Kotlin 2.3+. Aura is two minor versions behind Kotlin, four minor behind Media3, six minor behind Hilt. Drift compounds. Compose Strong Skipping (default Compose Compiler 2.x) is a free ~20 % LazyGrid recomposition win once the BOM bumps.
- **Scope:** AGP 8.7.3 → 9.2.x, Gradle 8.12 → 9.5+, Kotlin 2.1.0 → 2.3.20, KSP1 → KSP2, Compose BOM `2024.12.01` → `2026.05.00`, Material 3 1.3.1 → 1.4.x, Hilt 2.53.1 → 2.59.x, Lifecycle 2.8.7 → 2.10.x, Navigation 2.8.5 → 2.9.8, Coroutines 1.9.0 → 1.10.x. Re-audit every `@Stable`/`@Immutable` annotation under Strong Skipping. Re-run `assembleDebug` / `testDebugUnitTest` / `lintDebug`. Verify NewPipeExtractor 0.24.8 still compiles against the new toolchain (it's the most fragile pin).
- **Risk:** R8 keep-rule regressions; Hilt 2.59 generation differences on Kotlin 2.3; KSP2 incremental cache may need a clean. Mitigation: feature freeze for this pass; track APK size + cold-start delta.
- **Fit 5 / Impact 4 / Effort 2 / Risk 3 / Deps 3 / Novelty 1 = 18 → upgraded to Now because it gates N-3, N-4, NX-2, NX-7.**

### N-2. Firebase BoM 34.x + admin auth via Firebase Custom Claims

- **Source(s):** [Firebase Android release notes](https://firebase.google.com/support/release-notes/android); [`protobuf` CVE-2024-7254 advisory in BoM 34](https://github.com/firebase/firebase-android-sdk/releases); [VoteRepository.kt:75 TODO](app/src/main/java/com/freevibe/data/repository/VoteRepository.kt#L75); [Firebase Custom Claims docs](https://firebase.google.com/docs/auth/admin/custom-claims).
- **Why now:** Aura ships Firebase BoM 33.7.0. BoM 34.x updates transitive `protobuf-javalite` past CVE-2024-7254 and removes the deprecated KTX libraries. The admin-device-hash check in `VoteRepository` is documented in code as spoofable on rooted devices; Custom Claims move authorization server-side. Real risk: community uploads continue scaling.
- **Scope:** Bump BoM to 34.x; migrate Firebase init off KTX-namespaced helpers if they go away; move `adminDeviceIdHashes` to a `custom_claims.admin` boolean enforced by RTDB Security Rules; ship `.rules` file in repo and CI-verify with `firebase deploy --only database:rules --project=verify`. Clear DB migration path required if RTDB → Firestore later (N-9 in Next).
- **Risk:** Existing community-upload sessions keyed on anonymous device ID will lose admin status until they refresh through the new claim. Mitigation: dual-check during a one-cycle window.
- **Fit 5 / Impact 4 / Effort 4 / Risk 4 / Deps 5 / Novelty 2 = 24 → NOW.**

### N-3. Subject Segmentation API + AGSL effects pipeline

- **Source(s):** [ML Kit Subject Segmentation reference](https://developers.google.com/ml-kit/vision/subject-segmentation/android); current pin `com.google.mlkit:segmentation-selfie:16.0.0-beta6`; [AGSL `RuntimeColorFilter` + `RuntimeXfermode` Android 16 docs](https://developer.android.com/about/versions/16/features); [AGSL Compose patterns](https://medium.com/androiddevelopers/agsl-made-in-the-shade-r-7d06d14fe02a); [Pixel Live Effects (Shape / Weather / Cinematic) coverage](https://9to5google.com/2025/06/10/android-16-qpr1-beta-2-adds-live-effects-section-to-wallpaper-picker/); [WallFlow smart-crop reference](https://github.com/ammargitham/WallFlow).
- **Why now:** Selfie-segmentation is still on `16.0.0-beta6` two years after Google's beta tag — production risk. Subject Segmentation API (API 24+, multi-subject) is GA and out of beta. AGSL `RuntimeColorFilter`/`RuntimeXfermode` is the path forward for Aura's image-effect pipeline (Aura currently composes effects in Canvas). Direct parity with Pixel's "Shape" cutout and "Cinematic" depth effect with no dependency on Pixel-only system features.
- **Scope:** Swap `ParallaxWallpaperService` segmenter to Subject Segmentation. Add `AgslEffectPipeline` that exposes `RuntimeShader`-backed filters reusable from wallpaper editor + live wallpapers. Ship three first-class effects: subject cutout with color-matched background ("Shape"); subject-aware depth parallax ("Cinematic", replacement for current Canvas parallax); subject-aware tint passthrough for weather wallpapers (subject untinted, background tinted).
- **Risk:** AGSL needs Android 13+. minSdk 26 forces a fallback path; current Canvas pipeline becomes the fallback. ML Kit unbundled APK overhead (~4.5 MB + JNI spike — see existing pitfall log in CHANGELOG, [googlesamples/mlkit#386](https://github.com/googlesamples/mlkit/issues/386)).
- **Fit 5 / Impact 5 / Effort 3 / Risk 4 / Deps 4 / Novelty 4 = 25 → NOW.**

### N-4. Android Photo Picker migration + Monochrome themed app icon + Android 16 `WallpaperDescription`

- **Source(s):** [Photo Picker behavior change Android 14+](https://developer.android.com/about/versions/14/changes/partial-photo-video-access); [Adaptive icons & monochrome](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive); [WallpaperDescription reference](https://developer.android.com/reference/android/app/wallpaper/WallpaperDescription); [WallpaperInstance reference](https://developer.android.com/reference/android/app/wallpaper/WallpaperInstance); [UndeadWallpaper #48 — monochrome request](https://github.com/maocide/UndeadWallpaper/issues/48); [Doodle issues on AMOLED variants](https://github.com/patzly/doodle-android/issues/38).
- **Why now:** Aura still declares `READ_EXTERNAL_STORAGE` (maxSdkVersion=28) and uses `ActivityResultContracts.OpenDocument()` for video / GIF. Image imports for community uploads and gallery wallpaper-crop should use Photo Picker — no permission prompt, better UX, scoped-storage compliant. Monochrome layer is a 30-minute fix that's been requested in every adjacent OSS app's tracker. WallpaperDescription/Instance is Android 16 baseline for letting one `WallpaperService` expose distinct home / lock / time-of-day variants — directly relevant to Aura's parallax + weather + AI wallpapers.
- **Scope:** Replace gallery `OpenDocument` for image-MIME paths with `PickVisualMedia` (multi-select for batch import). Add `<adaptive-icon><monochrome>` drawable layer to `mipmap-anydpi-v26/ic_launcher.xml`. Declare `WallpaperDescription` metadata on `VideoWallpaperService`, `WeatherWallpaperService`, `ParallaxWallpaperService` so the system picker treats them as a single configurable engine instead of three separate live wallpapers. Wire matching `WallpaperInstance` for the home/lock split where Aura's `applyByLocator` already supports it.
- **Risk:** WallpaperDescription is Android 16+ only — keep legacy `<service>` declarations as fallback.
- **Fit 5 / Impact 4 / Effort 4 / Risk 5 / Deps 5 / Novelty 3 = 26 → NOW.**

### N-5. Aura Originals — bundled CC0 sound pack (long-promised Phase 1.1)

- **Source(s):** Aura ROADMAP Phase 1.1 (was P0 in prior priority matrix); [Freesound API + CC0 license walkthrough](https://opensource.creativecommons.org/blog/entries/freesound-intro/); [F-Droid Inclusion How-To](https://f-droid.org/docs/Inclusion_How-To/); [iOS 17/18 tone packs as cultural reference](https://www.zedge.net/find/ringtones/discord); [Ringdroid retirement signal making this niche open](https://forum.f-droid.org/t/ringtone-maker-app/22600); [WorkManager 2.10 + setExpedited download pattern](https://developer.android.com/reference/androidx/work/WorkRequest.Builder#setExpedited(androidx.work.OutOfQuotaPolicy)).
- **Why now:** First-run currently demands the network. The "Aura Picks" carousel went URL-backed-prebuffered in v6.13.0 but the actual bundle never shipped. Every commercial competitor ships day-one content; Aura's instant-startup story is undermined the moment the user disables Wi-Fi or hits a rate limit.
- **Scope:** Curate 200–500 CC0 sounds across ringtones (8–30s), notifications (1–5s), alarms (10–40s). Audit each for CC0 attribution + sha256 manifest for retroactive removal (per existing Round 3 note). Ship as a `WorkManager` first-launch download (~30 MB OGG) into `filesDir/aura_originals/` rather than bloating the APK. Update Room schema with `is_bundled` flag + `sha256` column (migration v14 → v15). Surface as "Aura Originals" tab + badge.
- **Risk:** CC0 misattribution on Freesound is well-documented; require a moderator review pass on every bundled file. Audio fidelity normalization needed (per Round 3 warning that preview-hq-mp3 is re-encoded; the bundle should use originals).
- **Fit 5 / Impact 5 / Effort 2 / Risk 3 / Deps 5 / Novelty 4 = 24 → NOW.**

---

## Next — queued, scored, ready

Eight items. All scored 18–23. Pull from the top of this list when Now closes.

### NX-1. GL/AGSL live wallpaper engine migration (T-9 reframed)

- **Source(s):** [AlynxZhou/alynx-live-wallpaper](https://github.com/AlynxZhou/alynx-live-wallpaper) (ExoPlayer + OpenGL ES reference); [maocide/UndeadWallpaper](https://github.com/maocide/UndeadWallpaper) (gapless OpenGL + ExoPlayer); [Media3 1.9 dav1d-based AV1 extension](https://android-developers.googleblog.com/2025/12/media3-190-whats-new.html); [Media3 1.6 pre-warming decoders](https://android-developers.googleblog.com/2025/03/media3-1-6-0-is-now-available.html); [patzly/pallax-android archive note](https://github.com/patzly/pallax-android) (Canvas-based live wallpapers were archived due to rendering inefficiency — direct cautionary tale); [GLSurfaceView RGB_565 banding pitfall](https://www.learnopengles.com/how-to-use-opengl-es-2-in-an-android-live-wallpaper/); [scale-types issue](https://github.com/AlynxZhou/alynx-live-wallpaper/issues/14).
- **Why next:** Aura's `VideoWallpaperService` uses `MediaPlayer` with `setVolume(0,0)`. Moving to Media3 ExoPlayer + AGSL/OpenGL pipeline lands four wins at once: gapless transitions; AV1 decode where hardware supports it; per-video focus rectangle / pan + zoom (Pixel Live Effects "Cinematic" parity); proper aspect-ratio handling. The existing Canvas-based parallax should also migrate behind AGSL `RuntimeColorFilter` for the same reason — Pallax was archived because Canvas live wallpapers can't keep up.
- **Scope:** Vendor a thin `GLWallpaperService` base in `com.freevibe.wallpaper.gl/`. Pause render thread on `Engine.onVisibilityChanged(false)`. Add `media3-ui-compose` for the preview surface. Replace `VideoWallpaperService` MediaPlayer path with ExoPlayer + `samplerExternalOES` shader. Keep the Canvas GIF renderer (already battery-bounded). Add Pan / Zoom / Focus controls in the apply sheet. Per-video FPS cap + quality preset (Wallpaper Engine parity).
- **Risk:** Largest refactor in 12 months. AV1 hardware decode is ~10 % of install base ([Meta engineering analysis](https://engineering.fb.com/2025/09/24/video-engineering/video-streaming-with-av1-video-codec-mobile-devices-meta-white-paper/)). Battery regression risk if the new pipeline skips invisible-pause.
- **Fit 4 / Impact 5 / Effort 1 / Risk 2 / Deps 3 / Novelty 4 = 19 → NEXT.**

### NX-2. Lockscreen depth — Subject-aware clock-tuck + lockscreen Glance widgets

- **Source(s):** [Android 16 QPR2 lock-screen widgets on phones](https://www.androidauthority.com/lock-screen-widgets-on-phones-android-16-qpr2-3589668/); [Glance 1.2 release notes](https://developer.android.com/jetpack/androidx/releases/glance); [One UI 8.5 Adaptive Lock Screen Clock](https://www.sammyfans.com/2025/09/28/one-ui-8-adaptive-lock-screen-clock/); [Nothing OS 4.1 depth effect](https://gadgets.beebom.com/guides/nothing-os-4-1-features); [iOS-style Depth Effect](https://www.one4studio.com/glossary/parallax-wallpaper); [Muzei issue #794 — different sources for lock and home](https://github.com/muzei/muzei/issues/794); [Doodle issue #92 — static lockscreen wallpaper](https://github.com/patzly/doodle-android/issues/92).
- **Why next:** Aura's `dualWallpapers` already handles home/lock pairs. What's missing is the *subject-aware* depth effect that iOS, Nothing OS, and One UI all ship. With N-3's Subject Segmentation in place, "clock tucks behind wallpaper subject" is a derivative feature. Lock-screen Glance widgets land on phones in Android 16 QPR2; Aura's existing Glance widget should opt-in (`not_keyguard` category check) so it can run as a lockscreen daily-pick.
- **Scope:** Generate clock-mask Bitmap on apply via subject segmentation; persist to `wallpaper_history` table. New live-wallpaper engine renders subject foreground layer over an artificially deepened background blur, with a hint surface that the system lockscreen renderer overlays the clock against. Ship a lockscreen Glance widget variant of Daily Pick. Add a "Lock screen only" option to wallpaper apply (Doodle parity).
- **Risk:** Engine-side clock-position estimation is heuristic on non-Pixel devices; ship as Pixel + Samsung allowlist initially.
- **Fit 5 / Impact 4 / Effort 2 / Risk 4 / Deps 3 / Novelty 5 = 23 → NEXT.**

### NX-3. Smart Crop with Subject Segmentation (Phase 6.5 finally)

- **Source(s):** Aura Phase 6.5 (never shipped); [ML Kit Subject Segmentation Android](https://developers.google.com/ml-kit/vision/subject-segmentation/android); [WallFlow Plus smart crop](https://github.com/ammargitham/WallFlow); [WallYou advanced cropping](https://github.com/you-apps/WallYou/issues/189); [Paperize vertical scrolling crop](https://github.com/Anthonyy232/Paperize/issues/428).
- **Why next:** N-3 lands Subject Segmentation; smart crop becomes a 2-day feature. Aura's existing pinch-zoom + aspect presets already give you most of the chrome; the missing piece is auto-positioning the crop rectangle to keep the primary subject in frame when reshaping landscape → portrait.
- **Scope:** Smart Crop toggle in `WallpaperCropScreen` + `VideoCropScreen`. When enabled, run Subject Segmentation, compute bounding box, center crop rectangle on it. Compare against rule-of-thirds heuristic for non-portrait outputs. Fall back to existing center-crop if confidence < 0.5.
- **Risk:** Slow on low-end devices; gate on Performance Class.
- **Fit 5 / Impact 4 / Effort 4 / Risk 5 / Deps 2 (depends on N-3) / Novelty 3 = 23 → NEXT.**

### NX-4. SelectedContentHolder removal (Phase 7.2)

- **Source(s):** Aura Phase 7.2; [Navigation Compose 2.9 type-safe routes](https://developer.android.com/jetpack/androidx/releases/navigation); [Lifecycle 2.10 `ViewModelScenario` for process-death testing](https://developer.android.com/jetpack/androidx/releases/lifecycle); existing `SelectedContentHolder.kt` (in-memory singleton).
- **Why next:** The singleton bridges screens but doesn't survive process death — a well-documented gotcha in CLAUDE.md. Navigation 2.9 type-safe routes can pass enums and value classes; combined with `SavedStateHandle`, you can replace the holder with a per-nav-graph ViewModel and serialize selection state. Removes a class of "wallpaper detail blank on resume" bugs.
- **Scope:** Move `selectedWallpaper`, `wallpaperList`, `selectedSound`, `pendingCategoryQuery` to a nav-graph-scoped `SelectionViewModel` backed by `SavedStateHandle`. Add a `ViewModelScenario` test for process-death restoration. Delete `SelectedContentHolder.kt`.
- **Risk:** Touches every detail/pager screen. Diff will be wide but mechanical.
- **Fit 5 / Impact 3 / Effort 3 / Risk 4 / Deps 3 / Novelty 1 = 19 → NEXT.**

### NX-5. Plugin / source ABI — Muzei-compatible "Aura Sources"

- **Source(s):** [Muzei Art Provider docs](https://api.muzei.co/); [MuzeiArtProvider source on GitHub](https://github.com/muzei/muzei/blob/main/muzei-api/src/main/java/com/google/android/apps/muzei/api/provider/MuzeiArtProvider.java); [Ian Lake's Muzei 3.0 announcement](https://medium.com/muzei/announcing-muzei-live-wallpaper-3-0-d167dd5795a4); [Pixiv4Muzei3 reference plugin](https://github.com/yellowbluesky/PixivforMuzei3); [HK Vision Muzei plugin reference](https://github.com/hossain-khan/android-hk-vision-muzei-plugin); [LiveWallpaperIt Muzei Reddit plugin](https://github.com/TBog/live-wallpaper-it); [Aura T-8 deferred note](docs/research/iter-1-scored.md); [Muzei plugin breaking-changes history](https://github.com/muzei/muzei/wiki/Changelog).
- **Why next:** Adopting Muzei's `MuzeiArtProvider` IPC contract lets Aura *consume* every existing Muzei source (Pixiv, Reddit, HK Vision, etc.) without writing any source-specific code; it also lets Aura *publish* itself as a Muzei source so Muzei users see Aura content. Two extensible ecosystems for the price of one. Avoids the "Muzei 1.x → 2.x broke everything" trap by versioning from day one.
- **Scope:** New `aura-sources` module exposing `AuraArtProvider` (Muzei-API-compatible). Wire `MuzeiArtSource` discovery via `PackageManager` query. Implement the inverse: a thin `MuzeiSourceBridge` repository that calls into installed Muzei providers and exposes their results in Aura's Discover. Version the contract from `v1`. Ship Pixiv source as a reference plugin in the repo.
- **Risk:** Muzei's API is GPL-3 in places; verify license bridge. Wear-Os mismatch warning in Muzei #869 — be careful.
- **Fit 4 / Impact 4 / Effort 2 / Risk 3 / Deps 3 / Novelty 5 = 21 → NEXT.**

### NX-6. Scheduler triggers — per-app exclusion, screen-off pre-stage, sub-15-min intervals, per-unlock

- **Source(s):** [Paperize #444 — per-app exclusion](https://github.com/Anthonyy232/Paperize/issues/444); [Paperize #482 — trigger on screen off](https://github.com/Anthonyy232/Paperize/issues/482); [Paperize #447 — specific time of change](https://github.com/Anthonyy232/Paperize/issues/447); [Paperize #126 — display events](https://github.com/Anthonyy232/Paperize/issues/126); [WallYou #229 — sub-15min interval](https://github.com/you-apps/WallYou/issues/229); [WallYou discussion #133 — anarkia47 widget for instant change](https://github.com/you-apps/WallYou/discussions/133); [Doubi88/SlideshowWallpaper #69 — force change](https://github.com/Doubi88/SlideshowWallpaper/issues/69); existing `AutoWallpaperWorker.kt`; existing `buildAutoWallpaperConstraints` helper (v6.12.0).
- **Why next:** All four asks recur across every OSS wallpaper-changer tracker. Aura already has the worker, constraints helper, and the SafeSearch + charging-only / Wi-Fi-only / idle toggles. The missing pieces are: per-app exclusion (compute foreground-app via `UsageStatsManager`); screen-off pre-stage (set wallpaper on `ACTION_SCREEN_OFF` so unlock shows the new one); rotation intervals shorter than the WorkManager 15-minute minimum (move to a `JobScheduler`-friendly `setExact` alarm); per-unlock trigger (`USER_PRESENT` broadcast).
- **Scope:** Permission-gated foreground-app reader. New rotation-trigger types in DataStore. `RotationTriggerService` listens to broadcasts. UI in `SettingsScreen` adds: "Exclude these apps", "Change on screen off", "Change on every unlock", "Change every N minutes (override 15-min minimum)". One-tap-shuffle Glance widget per the WallYou ask.
- **Risk:** `USER_PRESENT` background restrictions on Android 12+; require foreground service for fast triggers, which is intrusive. Mitigate with a clear opt-in banner.
- **Fit 5 / Impact 4 / Effort 3 / Risk 3 / Deps 5 / Novelty 3 = 23 → NEXT.**

### NX-7. Favorites sync via Firestore + Google sign-in (Phase 7.3)

- **Source(s):** Aura Phase 7.3; existing `default_web_client_id` blocker noted in Phase 4.3; [Firebase Auth Android docs](https://firebase.google.com/docs/auth/android/google-signin); [Room 2.8 schema defaults](https://developer.android.com/jetpack/androidx/releases/room); [Firestore offline persistence](https://firebase.google.com/docs/firestore/manage-data/enable-offline); [Aura existing FavoritesExporter](app/src/main/java/com/freevibe/service/FavoritesExporter.kt).
- **Why next:** Once N-2 lands BoM 34 + Custom Claims, the Google sign-in OAuth client can be wired without a separate trust review. Favorites are the only stateful user data not already in cloud (community uploads + votes + creator follows already are). Sync lets users move devices without losing their library and lays the ground for Wear OS / desktop companions.
- **Scope:** Google sign-in optional (no degradation for anonymous users). New Firestore collection `users/{uid}/favorites` with bidirectional sync against the local Room favorites table. Conflict resolution = last-write-wins by timestamp. Reuse FavoritesExporter's JSON schema for cross-device interop. Strong test coverage for sign-in / sign-out state changes.
- **Risk:** Firestore quota; Anonymous-Firebase-identity → Google-auth account-link path is fragile (must `linkWithCredential` not re-create). Document the failure mode for users who sign in on two devices simultaneously.
- **Fit 4 / Impact 4 / Effort 2 / Risk 3 / Deps 3 (N-2) / Novelty 2 = 18 → NEXT.**

### NX-8. Distribution to F-Droid + IzzyOnDroid + Obtainium

- **Source(s):** [F-Droid Inclusion How-To](https://f-droid.org/docs/Inclusion_How-To/); [F-Droid Reproducible Builds docs](https://f-droid.org/en/docs/Reproducible_Builds/); [F-Droid in 2025 retrospective](https://f-droid.org/en/2026/01/23/fdroid-in-2025-strengthening-our-foundations-in-a-changing-mobile-landscape.html); [F-Droid 1.19+ Session Installer background updates](https://f-droid.org/2024/02/01/twif.html); [IzzyOnDroid repo docs](https://apt.izzysoft.de/fdroid/index/info); [Obtainium app](https://github.com/ImranR98/Obtainium); [APK splits per-ABI reference](https://cdmunoz.medium.com/goodbye-giant-apk-how-we-went-from-186-mb-to-62-mb-with-split-per-abi-and-three-lines-in-ci-673dd71dbdcb); existing `.github/workflows/release.yml`.
- **Why next:** Aura's only distribution channel is signed GitHub Releases. The OSS Zedge-alternative pitch demands F-Droid presence. IzzyOnDroid is the path of least friction; Obtainium asks only for a structured release manifest. Per-ABI splits cut the APK from a single universal binary to four lean ones — meaningful since youtubedl-android bundles Python 3.8.
- **Scope:** Ship `fastlane/metadata/android/en-US/{short_description.txt,full_description.txt,changelogs/,images/}`. Verify reproducible builds via `apksigner` + `--ks-key-alias` (per F-Droid docs). Submit to IzzyOnDroid first. Add `splits { abi { ... } }` to `app/build.gradle.kts` with per-ABI release outputs. Update release workflow to attach all four APKs + a universal. Add an `obtainium` JSON manifest at repo root. Open the F-Droid metadata PR last (it's the slowest review).
- **Risk:** F-Droid forbids non-free dependencies. Firebase Storage may push you to the IzzyOnDroid track only (which permits proprietary deps). NewPipe Extractor + youtubedl-android are GPL and OK.
- **Fit 5 / Impact 4 / Effort 3 / Risk 4 / Deps 3 / Novelty 2 = 21 → NEXT.**

---

## Later — scoped, deferred

### L-1. Wear OS 6 companion via Watch Face Push API (was Phase 8.2)

- **Source(s):** [Watch Face Push API training](https://developer.android.com/training/wearables/watch-face-push); [Phone-side companion docs](https://developer.android.com/training/wearables/watch-face-push/phone-app); [Androidify on Wear OS](https://android-developers.googleblog.com/2025/12/bringing-androidify-to-wear-os-with.html); [Facer 5.0 Wear OS 6 features](https://news.facer.io/massive-facer-update-wear-os-6-new-features-for-all-7bb1480b5797); [cmota/Unsplash KMP Wear OS](https://github.com/cmota/Unsplash); [Watch Face Format docs](https://developer.android.com/training/wearables/wff).
- **Why later:** Wear OS 6 install base is small (Pixel Watch 4 launch). Watch Face Push API requires `minSdk=33` on the watch and is restricted to one face per marketplace app. Aura's novelty: a watch face *generated* from the user's currently-applied wallpaper — palette extraction (Aura already has it), complications derived from Material You tonal palette, optional Aura-Originals chime as the watch's "tick" sound.
- **Scope:** Phone-side companion only (no separate Wear OS app at first). Add a `WatchFaceCompositor` that reads `current_wallpaper.palette` + clock font + complication set → emits a WFF XML and pushes via Data Layer + Watch Face Push API. Surface as Settings → "Send to my Wear OS watch".
- **Fit 4 / Impact 3 / Effort 1 / Risk 3 / Deps 1 / Novelty 5 = 17 → LATER.**

### L-2. Tasker plugin (events / states / actions)

- **Source(s):** [Tasker plugin spec](https://tasker.joaoapps.com/plugins-intro.html); [Peristyle external intent example `app.peristyle.START_AUTO_WALLPAPER_SERVICE`](https://github.com/Hamza417/Peristyle); [Muzei Tasker integration since 3.0](https://medium.com/muzei/announcing-muzei-live-wallpaper-3-0-d167dd5795a4).
- **Why later:** Tasker integration is the cheapest "10x your trigger surface" feature. Once NX-6 lands, exposing every rotation trigger as a Tasker action / state is a small follow-up — but cheap to defer.
- **Scope:** A `TaskerPluginActivity` host for `ACTION_EDIT_SETTING` + `ACTION_FIRE_SETTING`. Actions: change wallpaper, change ringtone, apply tone pack. Events: wallpaper changed, source X returned 429. States: current source name, last applied wallpaper URL.
- **Fit 5 / Impact 3 / Effort 4 / Risk 5 / Deps 4 (NX-6) / Novelty 2 = 23 — would be Next but capacity-bounded behind NX-1..NX-8. Hold.**

### L-3. RTDB → Firestore migration for community votes (T-10)

- **Source(s):** Aura T-10 deferred note; [Firebase RTDB free-tier limits](https://firebase.google.com/pricing) (100 concurrent, 10 GB/month); existing `VoteRepository.kt` ConcurrentHashMap pattern.
- **Why later:** Still no telemetry showing Aura is approaching the cap. Voting writes are short, batched, and well-behaved. Migrate when (a) we have telemetry, (b) Firestore Custom Claims are already deployed (post-N-2). Until then, accept the risk.
- **Fit 3 / Impact 3 / Effort 1 / Risk 2 / Deps 3 / Novelty 2 = 14 → LATER.**

### L-4. KMP shared logic (foundation for desktop / future iOS)

- **Source(s):** [panels-art/WallApp](https://github.com/panels-art/WallApp) (KMP wallpaper app reference); [cmota/Unsplash KMP](https://github.com/cmota/Unsplash); [Splashy](https://github.com/ishubhamsingh/Splashy); [Coil 3 Compose Multiplatform](https://coil-kt.github.io/coil/upgrading_to_coil3/); [NewPipeExtractor-KMP fork](https://github.com/yushosei/NewPipeExtractor-KMP); [Compose for TV 1.0](https://developer.android.com/jetpack/androidx/releases/tv); Aura Phase 8.3 (desktop companion stretch).
- **Why later:** Coil 3 unlocks Compose Multiplatform image loading. Splitting Aura's `data/` layer (repositories + models) into a KMP module is invasive. Worth it once a desktop or TV companion is on the calendar. Until then, expensive churn.
- **Fit 3 / Impact 3 / Effort 1 / Risk 2 / Deps 1 (Coil 3) / Novelty 4 = 14 → LATER.**

### L-5. Desktop companion (Tauri/Compose Multiplatform)

- **Source(s):** Aura Phase 8.3; [Wallpaper Engine on Android](https://www.wallpaperengine.io/android/en) (cross-platform sync model); [Tauri docs](https://v2.tauri.app/).
- **Why later:** Depends on L-4 (KMP). The Wallpaper Engine desktop ↔ mobile sync pattern is the right model — favorites + collections in cloud, applied via per-platform engines.
- **Fit 3 / Impact 3 / Effort 1 / Risk 2 / Deps 1 / Novelty 3 = 13 → LATER.**

### L-6. Audio visualizer wallpaper / edge-light (Muviz-style)

- **Source(s):** [Muviz Edge feature review](https://www.fastgazi.com/2025/10/muviz-edge-stylish-music-visualizer.html); [Spectrolizer Play Store](https://play.google.com/store/apps/details?id=com.aicore.spectrolizer); [Spotify dynamic backdrop pattern explainer](https://medium.com/@shanmugashree3/how-spotify-creates-those-stunning-backdrops-that-match-every-song-playlist-00fe13eab033); existing `WeatherWallpaperService` Canvas pipeline.
- **Why later:** Net-new live wallpaper engine. Reuses Aura's existing Canvas + palette extraction. Big visual surface but smaller-than-it-looks user demand on OSS forums; lots of commercial competitors.
- **Fit 4 / Impact 3 / Effort 3 / Risk 4 / Deps 2 / Novelty 3 = 19 — would be Next but capacity-bounded. Hold.**

### L-7. Additional Glance widgets — Daily Wallpaper, Sound Quick-Set, Scheduler Controls

- **Source(s):** Aura Phase 7.4; [Glance 1.2 + `androidx.glance.wear` group](https://developer.android.com/jetpack/androidx/releases/glance); [WallYou widget request](https://github.com/you-apps/WallYou/discussions/133).
- **Why later:** Aura already ships a shuffle widget. Adding three more is mechanical but resizable variants (2x2/4x2/4x4) take time. Bundle with NX-2's lock-screen Glance widget work — share the layout system.
- **Fit 4 / Impact 3 / Effort 3 / Risk 5 / Deps 3 (NX-2) / Novelty 1 = 19 — hold.**

### L-8. True offline mode + prefetch + < 1.5s cold start

- **Source(s):** Aura Phase 7.5; existing `OfflineFavoritesManager` (80 MB/file, 512 MB total cap); [Baseline Profiles docs](https://developer.android.com/topic/performance/baselineprofiles/overview); [Strong Skipping perf wins](https://developer.android.com/develop/ui/compose/performance/stability/strongskipping).
- **Why later:** Aura's startup is already fast (Discover feed cached). Real benefit kicks in once N-1 lands Strong Skipping + Compose Compiler 2.x. Generate baseline profiles for the five top-traffic screens (Wallpapers, Videos, Sounds, Favorites, WallpaperDetail). Tighten Coil 3 disk cache. Prefetch next 10 from scheduler source on Wi-Fi.
- **Fit 4 / Impact 3 / Effort 3 / Risk 5 / Deps 2 (N-1) / Novelty 1 = 18 — hold behind N-1.**

### L-9. Wallpaper presets / "Setups" bundle export (Phase 8.1)

- **Source(s):** Aura Phase 8.1; KWGT `.kwgt` bundle pattern; [Backdrops Streak Collections](https://backdrops.io/) as anti-pattern; existing `CollectionExporter`.
- **Why later:** A natural extension of Shareable Collections. Adds wallpaper + recommended ringtone + tone pack + widget config to a single `.aura` zip with deep-link. Defer until N-5 (Aura Originals) and NX-5 (plugin ABI) are in.
- **Fit 4 / Impact 3 / Effort 3 / Risk 4 / Deps 3 / Novelty 3 = 20 — hold.**

---

## Under Consideration — needs scoping or charter call

### U-1. HDR / Ultra HDR (gainmap) wallpaper support
- **Source(s):** [Android 14 Ultra HDR](https://developer.android.com/media/grow/ultra-hdr/display); [BT.2020 / Display-P3 color management](https://source.android.com/docs/core/display/color-mgmt).
- **Open question:** Is the visual benefit worth the ICC-profile preservation rework? Pixel 8+ camera default; Samsung clamps to SDR. Charter: yes — Aura keeps quality high; deferred only on capacity.

### U-2. On-device Stable Diffusion via Snapdragon NPU
- **Source(s):** [xororz/local-dream](https://github.com/xororz/local-dream); [Qualcomm Stable Diffusion on 8 Gen 3 demo](https://www.qualcomm.com/news/onq/2024/02/worlds-first-on-device-demonstration-of-stable-diffusion-on-android); [Qualcomm Depth-Anything-V2 TFLite](https://huggingface.co/qualcomm/Depth-Anything-V2).
- **Open question:** Charter previously rejected on-device AI generation (R-1). Local-dream proves viability on a narrow chipset slice. NPU install base still <30 %. Hold until Snapdragon 8 Gen 4 / 5 baseline shifts.

### U-3. Pixiv source plugin
- **Source(s):** [PixivforMuzei3](https://github.com/yellowbluesky/PixivforMuzei3); requires OAuth2 + NSFW filtering tiers.
- **Open question:** Charter fit — sourcing art with legal/redistribution boundaries unclear. Defer to NX-5 plugin ecosystem.

### U-4. AI Sound Generation (Phase 3.2)
- **Source(s):** Aura Phase 3.2 (charter-pruned in v5.0.0); MusicGen / Riffusion abandoned per [community signal](https://news.ycombinator.com/item?id=38418254).
- **Open question:** MusicGen has no updates since 2024; Riffusion's Android app was pulled in Jul 2024. Server-side via Replicate is feasible but adds another paid-API key (charter friction). Keep rejected at the strong sense; reconsider if a credible OSS generator emerges.

### U-5. xHE-AAC ringtone output
- **Source(s):** [xHE-AAC (USAC) Wikipedia](https://en.wikipedia.org/wiki/Unified_Speech_and_Audio_Coding); Android 13+ native decoder; Aura's existing AudioTrimmer FFmpeg pipeline.
- **Open question:** 12–300 kbps with loudness/DRC built-in. Output target only — most user content arrives as MP3/AAC. Defer until users hit fidelity ceiling on alarms.

### U-6. AGSL shader playground (mini-KLWP for live wallpapers)
- **Source(s):** [ShaderEditor](https://github.com/markusfisch/ShaderEditor); [AGSL Compose patterns](https://medium.com/androiddevelopers/agsl-made-in-the-shade-r-7d06d14fe02a); [lwp-shaders curated library](https://github.com/cipold/lwp-shaders).
- **Open question:** Could ship a curated shader gallery (5–10 presets: plasma, lava, particles, water) without exposing an editor. Editor is out of scope; gallery is in scope. Decide when L-6 reaches Now.

### U-7. Health Connect-driven wallpapers
- **Source(s):** [Health Connect granular permissions](https://developer.android.com/health-and-fitness/guides/health-connect); KLWP step count integration as reference.
- **Open question:** Charter call: Aura is a personalization app, not a fitness app. "Wallpaper that grows with your step count" is a novel ambient nudge. Hold for community signal.

### U-8. Bluetooth-aware ringtone routing (different sound on speaker vs headset)
- **Source(s):** Android in-band ringing mandatory since 8.1 ([XDA reference](https://www.xda-developers.com/android-pie-bluetooth-in-band-ringtones-default/)); [Android Central thread on the gap](https://forums.androidcentral.com/threads/can-i-get-phones-ringtone-in-bluetooth-headset.1005511/).
- **Open question:** Significant engineering surface (AudioRouting hooks, requires `MODIFY_AUDIO_ROUTING` system permission Aura cannot get). Likely impossible without OEM bridge. Hold.

### U-9. Spoken caller-ID accessibility
- **Source(s):** [Google Accessibility — Talkback caller-ID](https://support.google.com/accessibility/android/answer/6006564).
- **Open question:** Samsung does this natively; AOSP doesn't. Small Kotlin TTS layer in `ContactRingtoneService` would suffice. Hold for explicit accessibility feedback.

### U-10. On-device image upscaling (RealSR / Real-ESRGAN / NCNN)
- **Source(s):** [RealSR-NCNN-Android](https://github.com/tumuyan/RealSR-NCNN-Android); [Qualcomm AI Hub Depth-Anything-V2](https://huggingface.co/qualcomm/Depth-Anything-V2); [ImageToolbox upscale features](https://github.com/T8RIN/ImageToolbox).
- **Open question:** Useful for low-res community uploads. APK size impact (~50–100 MB NCNN model). Maybe ship as an optional Aura Sources plugin (per NX-5) rather than in-app.

### U-11. Localization & RTL audit
- **Source(s):** Existing Locale.ROOT sweeps in CHANGELOG (v5.7, v5.22, v6.7) prove the foundation is laid; no actual translations shipped. Aura's only string resource locale is `values/strings.xml` (English). Compose RTL support is on by default but no audit has confirmed correct mirroring of wallpaper grids, sound waveforms, or scrubber direction.
- **Open question:** Which locales are first? Community signal from F-Droid issue trackers ([PixivforMuzei3 #254 Traditional Chinese](https://github.com/yellowbluesky/PixivforMuzei3/issues/254) is one example of how the request lands) suggests Brazilian Portuguese, German, French, Spanish, Russian, Simplified + Traditional Chinese, Japanese, Korean, Arabic + Hebrew (RTL) are the typical first 10 for an OSS Android app. Adopt Weblate or Crowdin's open-source plan. Audit `WallpaperDetailScreen` + `SoundDetailScreen` + `SoundEditorScreen` for RTL bugs (scrubber direction is the usual one).
- **Score:** Fit 4 / Impact 3 / Effort 3 / Risk 5 / Deps 5 / Novelty 1 = 21. Tier-wise this is Next-ready; held in Under Consideration only because the translator pipeline (Weblate vs. Crowdin vs. self-hosted) is a community-process decision, not an engineering one.

### U-12. Contributor docs (CONTRIBUTING.md, ARCHITECTURE.md, plugin authoring guide)
- **Source(s):** Existing `AGENTS.md` redirects to `CLAUDE.md` (49 KB working notes — internal, not contributor-facing); README has no contributor section beyond "Issues and PRs welcome"; no `CONTRIBUTING.md` in repo root; no `ARCHITECTURE.md`. NX-5 plugin ABI is meaningless without a plugin-authoring guide.
- **Open question:** Move the public-facing architecture content out of `CLAUDE.md` (keep the working-notes file for internal context) into a versioned `docs/ARCHITECTURE.md`. Write a `CONTRIBUTING.md` covering: build prereqs, branch/PR conventions, test-running, Aura Sources plugin contract once NX-5 lands. Add a `docs/plugins/` directory with example Muzei-compatible source.
- **Score:** Fit 5 / Impact 3 / Effort 4 / Risk 5 / Deps 3 (NX-5) / Novelty 1 = 21. Held until NX-5 settles to avoid stale plugin docs.

### U-13. Testing infrastructure expansion (Paparazzi screenshot tests + more instrumented coverage)
- **Source(s):** Existing 46 unit-test files; no Compose screenshot tests; one `androidTest/` smoke suite. [Paparazzi](https://github.com/cashapp/paparazzi) is the de-facto Compose screenshot library; [Roborazzi](https://github.com/takahirom/roborazzi) is a modern alternative.
- **Open question:** Screenshot test the AMOLED theming + RTL mirroring (ties to U-11). Add instrumented tests for `WallpaperApplier.applyByLocator` across `http://`, `file://`, `content://` URIs (the v6.15.0 bug-class). Pair with the toolchain triad (N-1) since Compose Compiler 2.x changes which composables are screenshot-stable.
- **Score:** Fit 4 / Impact 3 / Effort 3 / Risk 4 / Deps 3 (N-1) / Novelty 1 = 18. Held until N-1 settles.

---

## Rejected — do not silently resurrect

### R-1. AI on-device wallpaper generation
- **Source(s):** Aura roadmap charter (v5.0.0 prune); now ambiguous after Phase 3.1 (Stability AI server-side) shipped in v6.14.0.
- **Verdict:** Stays Rejected for *on-device* generation. Server-side Stability AI is the supported path. Re-litigate when Snapdragon NPU is mainstream and the [`local-dream`](https://github.com/xororz/local-dream) pattern stabilizes.

### R-2. Freesound OAuth2 for full-quality (non-preview) audio
- **Source(s):** ROADMAP Round 3 note; [Freesound API auth tiers](https://freesound.org/help/developers/).
- **Verdict:** Preview HQ MP3 (128 kbps) is already at typical ringtone fidelity. OAuth2 flow + token refresh storage is disproportionate. Stays Rejected unless users explicitly demand full-quality downloads.

### R-3. Subscription / coin / streak economy
- **Source(s):** [Backdrops Streak Collections terms](https://backdrops.io/terms/); [Wonder $4.99/wk complaints](https://support.google.com/googleplay/thread/214242312/); [Lensa dark-pattern reports](https://www.complaintsboard.com/wallcraft-wallpapers-live-b148917); [MKBHD Panels shutdown](https://www.macrumors.com/2025/12/01/mkbhd-wallpaper-app-shutdown/).
- **Verdict:** Rejected. Charter is OSS, MIT, no surprise charges. Community signal (r/Android, r/androidapps, Trustpilot) explicitly seeks escape from these patterns. Donations (GitHub Sponsors / Liberapay) are the only acceptable monetization vector. Do not propose again.

### R-4. Ads (banner, interstitial, rewarded video)
- **Source(s):** [Zedge ad cadence complaints](https://forums.androidcentral.com/threads/zedge-harmful.979866/page-2); [Trend Micro wallpaper-app ad-fraud report](https://www.trendmicro.com/en_us/research/18/l/android-wallpaper-apps-found-running-ad-fraud-scheme.html); WallCraft's AdMob integration (real anti-pattern in adjacent OSS).
- **Verdict:** Rejected. Same rationale as R-3. Aura's existence is to be Zedge without the ads. Re-proposing requires owner override.

### R-5. Selling user data / non-anonymous telemetry
- **Verdict:** Rejected. Not negotiable. Aura's optional telemetry (already partly shipped via `SourceMetrics` in-session diagnostics) is local-only.

### R-6. Full WYSIWYG live-wallpaper scripting (KLWP-grade)
- **Source(s):** [KLWP feature surface](https://docs.kustom.rocks/docs/downloads/download-klwp/).
- **Verdict:** Rejected. Aura's charter is curation + personalization, not authoring tools. KLWP is a programming environment with an enormous runtime risk surface; Aura would balloon to maintain it. The U-6 "shader gallery" is the acceptable subset.

---

## Themes — cross-cutting initiatives

Themes group Now/Next items so they ship coherently rather than as one-off features.

### T-A. Dependency hygiene & platform parity
Spans: **N-1, N-2, N-3, N-4**.
Outcome: Aura runs on the current platform with current libraries. Compose Strong Skipping wins, Material 3 Expressive, Photo Picker, WallpaperDescription, Subject Segmentation all land together.

### T-B. Lockscreen depth & live-wallpaper engine
Spans: **N-3, NX-1, NX-2, NX-3**.
Outcome: Aura matches Pixel Live Effects (Shape / Weather / Cinematic) and One UI Wonderland feature parity without depending on Pixel-only or Samsung-only system features.

### T-C. Extension ecosystem
Spans: **NX-5, L-2**.
Outcome: Aura ships as a Muzei source, consumes Muzei sources, exposes Tasker hooks. Third-party sources land without forking; the user's existing automation works with Aura.

### T-D. Multi-surface presence
Spans: **NX-2 (lockscreen), L-1 (Wear OS), L-4/L-5 (KMP/desktop), Compose-for-TV future**.
Outcome: One wallpaper choice propagates to lock screen, watch face, TV screensaver, desktop wallpaper. The "personalization graph" is a defensible novelty.

### T-E. Content authenticity & creator economy
Spans: **N-5 (Aura Originals), existing 4.x (community uploads / creator profiles / shareable collections), tip-jar follow-up**.
Outcome: Aura grows a curated catalog without becoming Zedge. Creators get attribution, follow counts, and donation paths; no marketplace, no coins.

### T-F. Distribution beyond Play Store
Spans: **NX-8, monochrome icon in N-4, per-ABI splits**.
Outcome: F-Droid / IzzyOnDroid / Obtainium / GitHub Releases. APK size cut. Reproducible builds where Firebase doesn't block them.

### T-G. Battery transparency & accessibility
Spans: existing 5.5 (battery dashboard), NX-1 (engine pause-on-invisible discipline), accessibility audits in U-8 / U-9.
Outcome: Aura's live wallpapers prove their power impact (Facer "Power Impact" rating equivalent). Users with TalkBack / large-font / reduced-motion needs get parity.

---

## Risk Register

Live operational risks ranked by likelihood × blast radius. Update at every release.

| Risk | Likelihood | Blast | Mitigation in roadmap |
|------|-----------|-------|-----------------------|
| NewPipe Extractor stops working on Play-Protect-certified Android (March 2026 maintainer warning, [piunikaweb](https://piunikaweb.com/2026/03/09/newpipe-certified-android-devices-warning/)) | Medium | High (YouTube sound tab dies) | Abstract `YouTubeRepository.search()` + `resolveStreamUrl()` behind a `SoundExtractor` interface in N-1; ship NewPipeExtractor as the default impl, `NewPipeExtractorKmpAdapter` ([yushosei/NewPipeExtractor-KMP](https://github.com/yushosei/NewPipeExtractor-KMP)) as fallback, and youtubedl-android as last-resort path. Pin `NewPipeExtractor` version comment already in place since v6.12.0; bump in lock-step with monthly upstream patches. |
| Firebase BoM 33.7.0 transitive protobuf vulnerable to CVE-2024-7254 | High | Medium | **N-2** bumps to BoM 34.x |
| Aura's `VoteRepository` admin auth is client-side spoofable | Medium | Medium (community moderation bypass) | **N-2** Custom Claims |
| ML Kit `segmentation-selfie:16.0.0-beta6` still in beta two years on | Medium | Medium (parallax breaks if pulled) | **N-3** migrates to Subject Segmentation GA |
| Stability AI free tier / pricing changes; per-user API key is the only path | Low | Medium (AI tab degrades to "bring your own key") | Document; consider Imagen via Firebase AI Logic in U-2 follow-up |
| AGP 9 / Kotlin 2.3 / KSP1 breaks Hilt/Compose generation | Medium | High | **N-1** coordinated upgrade with feature freeze |
| AV1 hardware decode <10 % install base; SW fallback burns battery | Medium | Medium | **NX-1** gates AV1 on Performance Class ≥ 33 |
| CISA-KEV Android framework CVE-2025-48572/-48633 on unpatched OEMs | Low | Low (device-level, not Aura's bug) | Optional one-time warning banner; defer |
| F-Droid inclusion blocked by Firebase Storage / yt-dlp Python | Medium | Medium (F-Droid track only) | **NX-8** targets IzzyOnDroid first |
| Wallhaven / Pexels / Pixabay ToS changes break aggregation | Low | High | NX-5 plugin ABI distributes sourcing risk to community plugins |
| Foreground-app reader for per-app rotation exclusion needs `PACKAGE_USAGE_STATS` | Medium | Medium (intrusive permission prompt) | **NX-6** ships opt-in banner explaining the trade-off |

---

## Shipped Inventory (Phase 1-8 detail, preserved from prior passes)

Kept verbatim — these are the receipts.

### Phase 1 — Content Foundation
- [ ] 1.1 Bundled "Aura Originals" CC0 sound pack — see N-5 above.
- [x] 1.2 Freesound v2 direct integration.
- [x] 1.3 SoundCloud CC (legacy retained for old saves; not in active feed since v6.18.0).
- [x] 1.4 Internet Archive dropped (DB v6→v7).
- [x] 1.5 Ringtone Maker from device music.

### Phase 2 — UX Overhaul
- [x] 2.1 Sounds-tab simplification.
- [x] 2.2 Instant sound preview (prebuffer first 5).
- [x] 2.3 QuickApplySheet long-press flow.
- [x] 2.4 Onboarding personalization + Settings re-entry.
- [x] 2.5 Seasonal content + Wallpapers banner + Sounds carousel.
- [x] 2.6 Sound Detail redesign.

### Phase 3 — AI & Generation
- [x] 3.1 AI Wallpaper Generation (Stability AI, server-side).
- [ ] 3.2 AI Sound Generation — see U-4 (Under Consideration).
- [x] 3.3 Parallax wallpapers (ML Kit + gyroscope).

### Phase 4 — Community
- [x] 4.1 User-generated sound uploads.
- [x] 4.2 User-generated wallpaper uploads.
- [x] 4.3 Creator profiles (anonymous identity; Google sign-in still queued in NX-7).
- [x] 4.4 Shareable collections.

### Phase 5 — Video Wallpaper Evolution
- [x] 5.1 Gallery video/GIF support + Fit/Fill controls.
- [x] 5.2 Loop & Crop editor with frame thumbnails.
- [x] 5.3 VFX particle overlays.
- [x] 5.4 Touch-reactive effects.
- [x] 5.5 Battery dashboard with auto FPS cap.

### Phase 6 — Smart Features
- [x] 6.1 Material You color preview.
- [x] 6.2 Dark/light auto-switch.
- [x] 6.3 Weather effects overlay.
- [x] 6.4 Time-of-day adaptive tint.
- [ ] 6.5 Smart Crop with subject detection — see NX-3.

### Phase 7 — Polish & Infra
- [x] 7.1 Unified Audio Service (MediaSession + cache).
- [ ] 7.2 Replace SelectedContentHolder — see NX-4.
- [ ] 7.3 Favorites sync — see NX-7.
- [ ] 7.4 Additional widgets — see L-7.
- [ ] 7.5 Performance & offline — see L-8.

### Phase 8 — Stretch (none shipped)
- 8.1 Wallpaper sets/theming — see L-9.
- 8.2 Wear OS — see L-1.
- 8.3 Desktop companion — see L-5.
- 8.4 Stickers / emoji — see U-?, hold.

---

## Implementation Log (preserved release-pass entries)

These are the dated receipts. The newest entries supersede the oldest where they overlap; do not edit prior entries.

### 2026-05-XX — Phase 3.1 AI Wallpaper Generation
- v6.14.0; `StabilityAiApi` Retrofit interface (multipart binary), `AiWallpaperRepository` (9:16 PNG, atomic write, `pruneOldFiles(50)`), `AiStyle` enum (8 presets), `AiWallpaperViewModel` (Hilt + DataStore key), `AiWallpaperScreen` (GlassCard, animated key field, prompt, style chips, shimmer, result apply/save).
- Entry: "AI" FilledTonalButton + AutoAwesome in WallpapersScreen.
- `STABILITY_AI_KEY` BuildConfig + `PreferencesManager.stabilityAiKey`.
- `ContentSource.AI_GENERATED` added.
- Phase 2.4 "Change your style" Settings entry confirmed already-shipped.
- Phase 5.3 VFX Particle Overlays confirmed already-shipped.

### 2026-05-14 — Gallery Video/GIF Import
- Phase 5.1 actionable slice: `ActivityResultContracts.OpenDocument()` accepts `video/*` and `image/gif`; copy to `live_wallpaper.<ext>`; `VideoWallpaperService` canvas-based GIF renderer.
- Removed dead "GIF not supported" Settings entry; tests cover GIF/WebM/3GP/MOV/MKV/MP4 extensions.
- Phase 5.2 already complete (frame thumbnails + loop range + FFmpeg trim).

### 2026-05-14 — Video Fit/Fill Apply Controls
- Phase 5.1 fit/fill/crop: apply confirmation exposes Fill and Fit before setup.
- `VideoWallpaperService` reads `scale_mode` and maps to MediaPlayer scale modes; GIF renderer honors same mode.
- Scale-mode normalization unit-tested.

### 2026-05-14 — Video Loop Trim Editor
- Phase 5.1/5.2: crop editor → Loop & Crop with start/end range.
- Preview seeks to loop start at loop end.
- FFmpeg gets `-ss`/`-t` for the selected segment.
- Loop-range coercion + FFmpeg trim arg tests.

### 2026-05-14 — Video Timeline Thumbnails
- Phase 5.2 last slice: up to six evenly-spread frames under the range scrubber.
- Failure-tolerant; falls back to plain slider when `MediaMetadataRetriever` fails.
- Tests cover frame-sampling positions and six-frame cap.

### 2026-05-14 — Touch-Reactive Effects (Phase 5.4)
- Weather wallpapers: ripple + spark bursts.
- Settings → Smart Features → Touch effects (Off / Subtle / Ripples + sparkles).
- Bounded, capped, battery-conscious.

### 2026-05-14 — Video Battery Dashboard (Phase 5.5)
- Settings → Video Wallpapers: live device battery, service heartbeat, active media type, effective FPS, scale mode, estimated impact.
- Auto 15 FPS cap below 15 % + not-charging.
- Dev FPS overlay toggle.

### 2026-04-25 — Product Polish
- Phase 2.4 Settings re-entry confirmed existed since prior session.
- Sounds COMMUNITY empty state: added "Upload a sound" CTA.
- DownloadsScreen broken-file badge via async `LaunchedEffect`.
- Phase 2.5 gap closed: Discover now biases Pexels + Pixabay by user style alongside Wallhaven.

### 2026-04-27 — Seasonal Content & Personalization
- Marked Phase 1.2/1.3/1.4, 2.3/2.6 done (previously shipped, unchecked).
- 2.5 `SeasonalContentManager`: Halloween (Oct 15–31), Holiday (Dec), New Year (Jan 1–3), Valentine (Feb 10–14), Summer (Jun 21–Sep 1).
- 2.4 wallpaper-Discover style-biased Wallhaven query when user styles set.
- `SeasonalContentManagerTest` covers all five windows + boundaries.

### 2026-04-25 — Diagnostics Follow-Up
- T-6 follow-up: `SourceMetrics` now covers Discover aggregate + Reddit + Bing + Pixabay + Pexels + Wallhaven variants + Openverse fallback + Freesound v2 + YouTube + SoundCloud + Audius + ccMixter.
- Diagnostics observes live in-session updates; chips + per-source rows.
- T-8 / T-9 / T-10 remain LATER.

### 2026-04-25 — Create From Music
- P0 1.5: Sounds > Create from music → system audio picker → SoundEditorScreen waveform loader.
- 30s default for long clips; 8–30s guidance; editor opens in Create Sound mode for local files.

### 2026-04-26 — Sounds Tab Chrome (Phase 2.1)
- Sounds tab exposes Ringtones / Notifications / Alarms as primary chips (not hidden behind source dropdown).
- YouTube / Community / Search in compact secondary menu.
- Quality bias in Refine bottom sheet.

### 2026-04-26 — Sound Discovery Carousel
- 2.1 last slice: tab-aware collection cards in feed.
- Long-press quick-apply keeps sheet open while applying; busy/disabled state in-place; permission gate with Grant action.
- Comparable-product research applied: Paperize collections, Muzei source clarity, ringtone-maker preview/apply emphasis.

### 2026-04-26 — Instant Sound Preview (Phase 2.2)
- 2.2 cache slice: first 5 visible preview URLs prebuffered into shared Media3 SimpleCache.
- `AudioPlaybackService` plays through same cache.
- Ready badge on cards.

### Older — see CHANGELOG.md for v5.x and earlier passes
- v6.15.0 deep audit: 11 bugs across v6.13–v6.14 (`WeatherUpdateWorker` Float precision, `SolarCalculator` DST, `SystemThemeListener` event-driven, `WallpaperApplier.applyByLocator` scheme-dispatch, `pruneOldFiles` finally called, `applyWallpaper` off Main, Stability AI HTTP-code mapping, ColorMatrix Paint caching, dark/light slot empty-state, VFX Cancel→Close).
- v6.12.0 Wallhaven SafeSearch toggles + auto-wallpaper rotation constraints + SourceMetrics in-session diagnostics + NewPipe stream-leak re-verify.
- v6.11.0 Freesound 429/Retry-After + Material You accent ladder + cancellation rethrow sweep.
- v6.10.0 finalized writes + widget intent safety + 64 MB editor download caps.
- v6.9.0 ColorExtractor 32 MB cap + SoundApplier 64 MB cap + Int-overflow harden.
- v6.8.0 video cropper hardening + 80 MB offline file cap + prefs write-order consistency.
- v6.7.0 bitmap-download 64 MB cap + Weather scaleBitmap leak fix + Locale.ROOT sweep + intent safety.
- v6.6.0 DownloadManager 64 MB ceiling + ParallaxWallpaperService segmenter double-close fix + AudioTrimmer bounded FFmpeg drain.
- v6.5.0 OOM-safe bitmap decode + HTTPS-only validation + accessibility touch targets.
- v6.4.0 structured-concurrency sweep across 16 catch sites.
- (continue back to v5.x in CHANGELOG.md).

---

## Appendix A — Cited OSS Competitors

Stars/dates as of research pass 2026-05-16.

- **Paperize** ([github.com/Anthonyy232/Paperize](https://github.com/Anthonyy232/Paperize)) — 1.1k★ — GPL-3.0 — fully-offline dynamic changer; Compose; v4.0.0-alpha live wallpaper mode. Issues cited: [#444](https://github.com/Anthonyy232/Paperize/issues/444), [#447](https://github.com/Anthonyy232/Paperize/issues/447), [#482](https://github.com/Anthonyy232/Paperize/issues/482), [#516](https://github.com/Anthonyy232/Paperize/issues/516), [#531](https://github.com/Anthonyy232/Paperize/issues/531), [#532](https://github.com/Anthonyy232/Paperize/issues/532), [#428](https://github.com/Anthonyy232/Paperize/issues/428), [#126](https://github.com/Anthonyy232/Paperize/issues/126), [#192](https://github.com/Anthonyy232/Paperize/issues/192); discussion [#313](https://github.com/Anthonyy232/Paperize/discussions/313).
- **WallFlow** ([github.com/ammargitham/WallFlow](https://github.com/ammargitham/WallFlow)) — 452★ — GPL-3.0 — Wallhaven + Reddit; foldable inner + outer; smart crop (Plus variant); Paging 3; KMP Windows planned. Issues: [#62](https://github.com/ammargitham/WallFlow/issues/62), [#63](https://github.com/ammargitham/WallFlow/issues/63), [#64](https://github.com/ammargitham/WallFlow/issues/64), [#68](https://github.com/ammargitham/WallFlow/issues/68), [#70](https://github.com/ammargitham/WallFlow/issues/70), [#73](https://github.com/ammargitham/WallFlow/issues/73), [#82](https://github.com/ammargitham/WallFlow/issues/82), [#91](https://github.com/ammargitham/WallFlow/issues/91), [#99](https://github.com/ammargitham/WallFlow/issues/99), [#102](https://github.com/ammargitham/WallFlow/issues/102).
- **WallCraft** ([github.com/Rahul-999-alpha/WallCraft](https://github.com/Rahul-999-alpha/WallCraft)) — 1★ — MIT — Pollinations.ai no-key AI generation, AMOLED, AdMob (anti-pattern for Aura).
- **Muzei** ([github.com/muzei/muzei](https://github.com/muzei/muzei)) — 4.9k★ — Apache-2.0 — refreshing-art live wallpaper; canonical plugin/source API. Issues: [#794](https://github.com/muzei/muzei/issues/794), [#800](https://github.com/muzei/muzei/issues/800), [#793](https://github.com/muzei/muzei/issues/793), [#792](https://github.com/muzei/muzei/issues/792), [#797](https://github.com/muzei/muzei/issues/797), [#869](https://github.com/muzei/muzei/issues/869), [#838](https://github.com/muzei/muzei/issues/838), [#836](https://github.com/muzei/muzei/issues/836), [#811](https://github.com/muzei/muzei/issues/811), [#128](https://github.com/muzei/muzei/issues/128), [#110](https://github.com/muzei/muzei/issues/110), [#109](https://github.com/muzei/muzei/issues/109).
- **Peristyle** ([github.com/Hamza417/Peristyle](https://github.com/Hamza417/Peristyle)) — 620★ — Apache-2.0 — glassmorphic Compose wallpaper mgr; tags + auto-changer; intent `app.peristyle.START_AUTO_WALLPAPER_SERVICE`.
- **UndeadWallpaper** ([github.com/maocide/UndeadWallpaper](https://github.com/maocide/UndeadWallpaper)) — 99★ — GPL-3.0 — OpenGL + ExoPlayer video wallpaper. Issues: [#5](https://github.com/maocide/UndeadWallpaper/issues/5), [#13](https://github.com/maocide/UndeadWallpaper/issues/13), [#24](https://github.com/maocide/UndeadWallpaper/issues/24), [#46](https://github.com/maocide/UndeadWallpaper/issues/46), [#47](https://github.com/maocide/UndeadWallpaper/issues/47), [#48](https://github.com/maocide/UndeadWallpaper/issues/48).
- **AlynxLiveWallpaper** ([github.com/AlynxZhou/alynx-live-wallpaper](https://github.com/AlynxZhou/alynx-live-wallpaper)) — 106★ — Apache-2.0 — reference ExoPlayer + OpenGL ES live wallpaper. Issues: [#14](https://github.com/AlynxZhou/alynx-live-wallpaper/issues/14), [#15](https://github.com/AlynxZhou/alynx-live-wallpaper/issues/15), [#16](https://github.com/AlynxZhou/alynx-live-wallpaper/issues/16).
- **GLWallpaperService** ([github.com/GLWallpaperService/GLWallpaperService](https://github.com/GLWallpaperService/GLWallpaperService)) — 153★ — Apache-2.0 — unmaintained, foundational GLEngine base class.
- **WallYou** ([github.com/you-apps/WallYou](https://github.com/you-apps/WallYou)) — 1k★ — GPL-3 — multi-source aggregator; auto-changer. Issues: [#189](https://github.com/you-apps/WallYou/issues/189), [#229](https://github.com/you-apps/WallYou/issues/229), [#267](https://github.com/you-apps/WallYou/issues/267); discussion [#133](https://github.com/you-apps/WallYou/discussions/133).
- **Doodle** ([github.com/patzly/doodle-android](https://github.com/patzly/doodle-android)) — 832★ — GPL-3 — Pixel-style colorful live wallpapers. Issues: [#29](https://github.com/patzly/doodle-android/issues/29), [#38](https://github.com/patzly/doodle-android/issues/38), [#77](https://github.com/patzly/doodle-android/issues/77), [#83](https://github.com/patzly/doodle-android/issues/83), [#92](https://github.com/patzly/doodle-android/issues/92), [#114](https://github.com/patzly/doodle-android/issues/114), [#115](https://github.com/patzly/doodle-android/issues/115), [#119](https://github.com/patzly/doodle-android/issues/119).
- **Pallax** ([github.com/patzly/pallax-android](https://github.com/patzly/pallax-android)) — 58★ — GPL-3 — ARCHIVED Jan 2025; cautionary tale on Canvas-based live wallpaper inefficiency.
- **DarkModeWallpaper** ([github.com/cvzi/darkmodewallpaper](https://github.com/cvzi/darkmodewallpaper)) — 222★ — GPL-3 — day/night wallpaper pair switching; animated GIF + WebP support. Issues: [#9](https://github.com/cvzi/darkmodewallpaper/issues/9), [#80](https://github.com/cvzi/darkmodewallpaper/issues/80), [#104](https://github.com/cvzi/darkmodewallpaper/issues/104).
- **SlideshowWallpaper** ([github.com/Doubi88/SlideshowWallpaper](https://github.com/Doubi88/SlideshowWallpaper)) — 74★ — GPL-3 — no-permission slideshow. Issues: [#62](https://github.com/Doubi88/SlideshowWallpaper/issues/62), [#64](https://github.com/Doubi88/SlideshowWallpaper/issues/64), [#65](https://github.com/Doubi88/SlideshowWallpaper/issues/65), [#69](https://github.com/Doubi88/SlideshowWallpaper/issues/69), [#70](https://github.com/Doubi88/SlideshowWallpaper/issues/70), [#74](https://github.com/Doubi88/SlideshowWallpaper/issues/74), [#75](https://github.com/Doubi88/SlideshowWallpaper/issues/75).
- **ShaderEditor** ([github.com/markusfisch/ShaderEditor](https://github.com/markusfisch/ShaderEditor)) — 1.1k★ — MIT — GLSL shaders as live wallpapers. Issues: [#251](https://github.com/markusfisch/ShaderEditor/issues/251), [#256](https://github.com/markusfisch/ShaderEditor/issues/256), [#259](https://github.com/markusfisch/ShaderEditor/issues/259), [#275](https://github.com/markusfisch/ShaderEditor/issues/275).
- **ShaderShowcaseApp** ([github.com/thelumiereguy/ShaderShowcaseApp](https://github.com/thelumiereguy/ShaderShowcaseApp)) — 280★ — GPL-3 — Compose + GLSL playground.
- **lwp-shaders** ([github.com/cipold/lwp-shaders](https://github.com/cipold/lwp-shaders)) — 21★ — MIT — curated GLSL shaders.
- **AlwaysOn** ([github.com/Domi04151309/AlwaysOn](https://github.com/Domi04151309/AlwaysOn)) — 218★ — GPL-3 — FOSS AOD. Issues: [#30](https://github.com/Domi04151309/AlwaysOn/issues/30), [#63](https://github.com/Domi04151309/AlwaysOn/issues/63), [#71](https://github.com/Domi04151309/AlwaysOn/issues/71), [#77](https://github.com/Domi04151309/AlwaysOn/issues/77), [#78](https://github.com/Domi04151309/AlwaysOn/issues/78), [#81](https://github.com/Domi04151309/AlwaysOn/issues/81), [#91](https://github.com/Domi04151309/AlwaysOn/issues/91), [#105](https://github.com/Domi04151309/AlwaysOn/issues/105).
- **ColorBlendr** ([github.com/Mahmud0808/ColorBlendr](https://github.com/Mahmud0808/ColorBlendr)) — 2.1k★ — GPL-3 — FabricatedOverlay Material You tweaks. Issues: [#247](https://github.com/Mahmud0808/ColorBlendr/issues/247), [#252](https://github.com/Mahmud0808/ColorBlendr/issues/252), [#254](https://github.com/Mahmud0808/ColorBlendr/issues/254), [#260](https://github.com/Mahmud0808/ColorBlendr/issues/260), [#262](https://github.com/Mahmud0808/ColorBlendr/issues/262), [#288](https://github.com/Mahmud0808/ColorBlendr/issues/288).
- **PixivforMuzei3** ([github.com/yellowbluesky/PixivforMuzei3](https://github.com/yellowbluesky/PixivforMuzei3)) — 203★ — GPL-3 — Pixiv Muzei source. Issues: [#184](https://github.com/yellowbluesky/PixivforMuzei3/issues/184), [#194](https://github.com/yellowbluesky/PixivforMuzei3/issues/194), [#227](https://github.com/yellowbluesky/PixivforMuzei3/issues/227), [#229](https://github.com/yellowbluesky/PixivforMuzei3/issues/229), [#234](https://github.com/yellowbluesky/PixivforMuzei3/issues/234), [#246](https://github.com/yellowbluesky/PixivforMuzei3/issues/246), [#254](https://github.com/yellowbluesky/PixivforMuzei3/issues/254).
- **LiveWallpaperIt** ([github.com/TBog/live-wallpaper-it](https://github.com/TBog/live-wallpaper-it)) — 11★ — GPL-3 — Reddit Muzei plugin. Issues: [#16](https://github.com/TBog/live-wallpaper-it/issues/16), [#18](https://github.com/TBog/live-wallpaper-it/issues/18), [#20](https://github.com/TBog/live-wallpaper-it/issues/20), [#21](https://github.com/TBog/live-wallpaper-it/issues/21), [#23](https://github.com/TBog/live-wallpaper-it/issues/23).
- **HK Vision Muzei plugin** ([github.com/hossain-khan/android-hk-vision-muzei-plugin](https://github.com/hossain-khan/android-hk-vision-muzei-plugin)) — 6★ — Apache-2.0 — clean current-gen Muzei source reference.
- **BingWallpaper** ([github.com/liaoheng/BingWallpaper](https://github.com/liaoheng/BingWallpaper)) — 153★ — GPL-3 — daily Bing image with 2-week browse.
- **local-dream** ([github.com/xororz/local-dream](https://github.com/xororz/local-dream)) — 2.4k★ — on-device SDXL via Snapdragon NPU. Issues: [#183](https://github.com/xororz/local-dream/issues/183), [#189](https://github.com/xororz/local-dream/issues/189), [#191](https://github.com/xororz/local-dream/issues/191), [#195](https://github.com/xororz/local-dream/issues/195), [#198](https://github.com/xororz/local-dream/issues/198), [#203](https://github.com/xororz/local-dream/issues/203), [#206](https://github.com/xororz/local-dream/issues/206), [#209](https://github.com/xororz/local-dream/issues/209), [#210](https://github.com/xororz/local-dream/issues/210).
- **AiWallpaperChanger** ([github.com/RikudouSage/AiWallpaperChanger](https://github.com/RikudouSage/AiWallpaperChanger)) — 9★ — MIT — AI Horde-based.
- **NewPipe** ([github.com/TeamNewPipe/NewPipe](https://github.com/TeamNewPipe/NewPipe)) — 38.2k★ — GPL-3 — privacy YouTube/PeerTube/Bandcamp/SoundCloud client. SABR coordination [#12248](https://github.com/TeamNewPipe/NewPipe/issues/12248).
- **NewPipeExtractor** ([github.com/TeamNewPipe/NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)) — extractor library Aura pins.
- **NewPipeExtractor-KMP** ([github.com/yushosei/NewPipeExtractor-KMP](https://github.com/yushosei/NewPipeExtractor-KMP)) — Compose Multiplatform fork.
- **Seal** ([github.com/JunkFood02/Seal](https://github.com/JunkFood02/Seal)) — 26.3k★ — GPL-3 — Compose yt-dlp UI. Issues: [#2377](https://github.com/JunkFood02/Seal/issues/2377), [#2391](https://github.com/JunkFood02/Seal/issues/2391), [#2398](https://github.com/JunkFood02/Seal/issues/2398), [#2426](https://github.com/JunkFood02/Seal/issues/2426), [#2453](https://github.com/JunkFood02/Seal/issues/2453), [#2470](https://github.com/JunkFood02/Seal/issues/2470), [#2471](https://github.com/JunkFood02/Seal/issues/2471), [#2474](https://github.com/JunkFood02/Seal/issues/2474), [#2499](https://github.com/JunkFood02/Seal/issues/2499), [#2518](https://github.com/JunkFood02/Seal/issues/2518).
- **ytdlnis** ([github.com/deniscerri/ytdlnis](https://github.com/deniscerri/ytdlnis)) — 8.6k★ — GPL-3 — yt-dlp + scheduled downloads + custom templates. Issues: [#1147](https://github.com/deniscerri/ytdlnis/issues/1147), [#1149](https://github.com/deniscerri/ytdlnis/issues/1149), [#1168](https://github.com/deniscerri/ytdlnis/issues/1168), [#1173](https://github.com/deniscerri/ytdlnis/issues/1173), [#1176](https://github.com/deniscerri/ytdlnis/issues/1176), [#1177](https://github.com/deniscerri/ytdlnis/issues/1177), [#1184](https://github.com/deniscerri/ytdlnis/issues/1184), [#1189](https://github.com/deniscerri/ytdlnis/issues/1189), [#1196](https://github.com/deniscerri/ytdlnis/issues/1196).
- **youtubedl-android** ([github.com/yausername/youtubedl-android](https://github.com/yausername/youtubedl-android)) — 1.3k★ — GPL-3 — yt-dlp wrapper.
- **Ringdroid (althafvly fork)** ([github.com/althafvly/ringdroid](https://github.com/althafvly/ringdroid)) — 61★ — de-facto Android ringtone editor; v3.0.1 May 2026 added Android 16 support; only actively maintained FOSS option.
- **RingtoneSmartKit** ([github.com/AmjdAlhashede/RingtoneSmartKit](https://github.com/AmjdAlhashede/RingtoneSmartKit)) — 6★ — Apache-2.0 — Kotlin library for system + contact ringtones.
- **UltimateRingtonePicker** ([github.com/DeweyReed/UltimateRingtonePicker](https://github.com/DeweyReed/UltimateRingtonePicker)) — 68★ — MIT — MediaStore-scoped picker with multi-select.
- **ImageToolbox** ([github.com/T8RIN/ImageToolbox](https://github.com/T8RIN/ImageToolbox)) — 12.9k★ — Apache-2.0 — 310+ filters, AI background removal, OCR, upscale. Issues: [#2759](https://github.com/T8RIN/ImageToolbox/issues/2759), [#2763](https://github.com/T8RIN/ImageToolbox/issues/2763).
- **freesound-android** ([github.com/futurice/freesound-android](https://github.com/futurice/freesound-android)) — 86★ — Apache-2.0 — unofficial Freesound client reference.
- **panels-art/WallApp** ([github.com/panels-art/WallApp](https://github.com/panels-art/WallApp)) — 73★ — Apache-2.0 — production KMP wallpaper app reference (panels.art).
- **cmota/Unsplash** ([github.com/cmota/Unsplash](https://github.com/cmota/Unsplash)) — 64★ — Apache-2.0 — Compose Multiplatform Android + Wear OS + iOS + Desktop + Web.
- **ishubhamsingh/Splashy** ([github.com/ishubhamsingh/Splashy](https://github.com/ishubhamsingh/Splashy)) — 51★ — Apache-2.0 — clean KMP Unsplash.
- **RealSR-NCNN-Android** ([github.com/tumuyan/RealSR-NCNN-Android](https://github.com/tumuyan/RealSR-NCNN-Android)) — 2k★ — on-device upscaling via NCNN/MNN.
- **workpaper-android** ([github.com/Jarvay/workpaper-android](https://github.com/Jarvay/workpaper-android)) — 18★ — MIT — scheduled wallpaper changer with MOV/MP4 video wallpaper support.
- **LiveSlider** ([github.com/rahulshah456/LiveSlider](https://github.com/rahulshah456/LiveSlider)) — 57★ — MIT — parallax slideshow live wallpaper reference.
- **zero** ([github.com/lucasasselli/zero](https://github.com/lucasasselli/zero)) — 71★ — GPL-3 — ARCHIVED; layered PNG 3D parallax reference.
- **BelecoLiveWallpaper** ([github.com/dklaputa/BelecoLiveWallpaper](https://github.com/dklaputa/BelecoLiveWallpaper)) — 82★ — historical OpenGL + Rotation Vector Sensor reference.
- **kmpalette** ([github.com/jordond/kmpalette](https://github.com/jordond/kmpalette)) — Compose Multiplatform Palette replacement.

---

## Appendix B — Cited Commercial / Adjacent Apps

- Zedge — [help.zedge.net/.../pAInt](https://help.zedge.net/hc/en-us/sections/11021867047060-Creating-content-with-Zedge-pAInt); [blog roundup](https://blog.zedge.net/best-wallpaper-apps/); ad complaints [Android Police](https://www.androidpolice.com/best-android-wallpaper-collections-apps-automated-parallax/), [Trustpilot](https://www.trustpilot.com/review/www.zedge.net), [PissedConsumer](https://zedge.pissedconsumer.com/review.html), [Android Central #979866](https://forums.androidcentral.com/threads/zedge-harmful.979866/page-2).
- Walli — [walliapp.com](https://www.walliapp.com/), [artists](https://www.walliapp.com/artists/), [reviews](https://justuseapp.com/en/app/1061097668/walli-cool-wallpapers-hd/reviews).
- Backdrops — [Terms](https://backdrops.io/terms/), [App Store description](https://apps.apple.com/us/app/backdrops-wallpapers/id1500143735), [Android Authority v6.0 review](https://www.androidauthority.com/backdrops-wallpaper-app-material-3-expressive-update-3577927/).
- Tapet — [Play Store](https://play.google.com/store/apps/details?id=com.sharpregion.tapet), [Premium summary](https://www.apkdone.com/tapet/), [subscription complaints](https://www.app-sales.net/sales/tapet-premium-upgrade-7152).
- Abstruct — [abstruct.co](https://abstruct.co/).
- WLPPR — [Play Store](https://play.google.com/store/apps/details?id=com.wlppr).
- Vellum — [getvellum.com](https://www.getvellum.com/).
- Resplash — [GitHub](https://github.com/b-lam/Resplash), [Play Store](https://play.google.com/store/apps/details?id=com.b_lam.resplash).
- WallpapersCraft / WallCraft — [ComplaintsBoard](https://www.complaintsboard.com/wallcraft-wallpapers-live-b148917).
- KLWP / KWGT — [docs.kustom.rocks](https://docs.kustom.rocks/docs/downloads/download-klwp/), [Play Store](https://play.google.com/store/apps/details?id=org.kustom.wallpaper).
- Muviz Edge — [fastgazi.com review](https://www.fastgazi.com/2025/10/muviz-edge-stylish-music-visualizer.html), [Play Store](https://play.google.com/store/apps/details?id=com.sparkine.muvizedge).
- Spectrolizer — [Play Store](https://play.google.com/store/apps/details?id=com.aicore.spectrolizer).
- Vizik — [Play Store](https://play.google.com/store/apps/details?id=com.banix.music.visualizer.maker).
- Audiko — [Play Store](https://play.google.com/store/apps/details?id=net.audiko2.pro), [Zedge ringtone roundup](https://blog.zedge.net/best-free-ringtone-apps/).
- Wonder — [Play Store](https://play.google.com/store/apps/details?id=com.codeway.wonder), [Google Play community complaint thread](https://support.google.com/googleplay/thread/214242312/).
- Lensa — wide critical coverage of dark patterns and bias.
- ImagineArt — [Play Store](https://play.google.com/store/apps/details?id=com.vyroai.aiart).
- Krea — [pricing](https://www.krea.ai/pricing), [API features](https://www.krea.ai/features/api).
- Facer — [news.facer.io WOS6 article](https://news.facer.io/massive-facer-update-wear-os-6-new-features-for-all-7bb1480b5797), [5.0 announcement](https://news.facer.io/introducing-facer-5-0-and-facer-premium/), [Android Authority hands-on](https://www.androidauthority.com/facer-app-hands-on-3583105/).
- WatchMaker — [WFF announcement](https://getwatchmaker.com/wff_announcement), [Android Authority Wear OS 6 coverage](https://www.androidauthority.com/pujie-watchmaker-watch-face-wear-os-6-support-3581417/).
- Pujie — [pujie.io](https://pujie.io/), [Play Store](https://play.google.com/store/apps/details?id=com.pujie.watchfaces).
- Glance (Motorola) — [Play Store](https://play.google.com/store/apps/details?id=com.glance.lockscreenM), [9to5Google coverage](https://9to5google.com/2024/04/26/glance-android-lockscreen-motorola-turn-off/).
- Always On AMOLED — [Play Store](https://play.google.com/store/apps/details?id=com.tomer.alwayson), [TechWiser roundup](https://techwiser.com/best-always-on-display-apps-on-android/).
- Samsung Good Lock — [Android Central guide](https://www.androidcentral.com/samsung-good-lock), [SamMobile Wonderland](https://www.sammobile.com/news/new-good-lock-module-wonderland-create-custom-live-wallpapers/), [Sammy Fans](https://www.sammyfans.com/2025/10/24/samsung-customization-with-good-lock/), [Samsung Newsroom](https://news.samsung.com/global/exploring-good-lock-%E2%91%A2-3-features-recommended-by-samsung-developers-and-newsroom-editors).
- One UI 8.5 — [Android Authority](https://www.androidauthority.com/samsung-one-ui-8-5-lock-screen-weather-effect-3630836/), [Digital Trends](https://www.digitaltrends.com/phones/samsungs-one-ui-8-5-will-turn-your-lock-screen-into-a-mini-music-show/).
- Pixel Live Effects (Android 16 QPR1) — [9to5Google](https://9to5google.com/2025/05/20/google-pixel-wallpaper-effects-android-16-qpr1/), [PiunikaWeb user reception](https://piunikaweb.com/2025/09/05/pixels-new-live-effects-wallpaper-feature-falls-flat-with-users/).
- Nothing Glyph SDK — [Glyph Developer Kit](https://github.com/Nothing-Developer-Programme/Glyph-Developer-Kit), [Glyph Matrix Developer Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit), [Nothing OS 4.1 features](https://gadgets.beebom.com/guides/nothing-os-4-1-features), [Android Central Nothing OS 4](https://www.androidcentral.com/phones/nothing-phones/nothing-os-4-arrives-for-the-phone-3-with-exclusive-features-refined-glyph-interface-and-more).
- Wallpaper Engine (Android companion) — [wallpaperengine.io/android](https://www.wallpaperengine.io/android/en), [Steam WE 2.0 announcement](https://store.steampowered.com/news/app/431960/view/3101285480922069754).
- Spotify dynamic backdrop pattern — [Medium analysis](https://medium.com/@shanmugashree3/how-spotify-creates-those-stunning-backdrops-that-match-every-song-playlist-00fe13eab033), [Envato design](https://elements.envato.com/learn/spotify-wrapped-design-aesthetic), [Eggradients on Spotify colors](https://www.eggradients.com/blog/spotify-colors).
- Procreate color sampling — [Procreate Color page](https://procreate.com/ipad/color), [Procreate Handbook palettes](https://help.procreate.com/procreate/handbook/5.0/colors/colors-palettes), [Pigeon Letters eyedropper](https://www.thepigeonletters.com/blogs/1/procreate-eyedropper).
- Apple Live Photos / Depth Effect — [Apple Support](https://support.apple.com/en-us/120734), [One4Studio glossary](https://www.one4studio.com/glossary/parallax-wallpaper), [Engadget iOS 26 Spatial Scenes](https://www.engadget.com/mobile/smartphones/how-to-make-your-lock-screen-background-holographic-in-ios-26-110049999.html).
- MKBHD Panels shutdown (anti-pattern lesson) — [MacRumors](https://www.macrumors.com/2025/12/01/mkbhd-wallpaper-app-shutdown/), [TechCrunch](https://techcrunch.com/2025/12/01/mkbhds-wallpaper-app-panels-is-shutting-down/).

---

## Appendix C — Platform, Standards, and Dependency Sources

- Android 14 docs — [Photo Picker behavior change](https://developer.android.com/about/versions/14/changes/partial-photo-video-access).
- Android 15 — [behavior changes all](https://developer.android.com/about/versions/15/behavior-changes-all), [summary](https://developer.android.com/about/versions/15/summary).
- Android 16 (Baklava) — [features](https://developer.android.com/about/versions/16/features), [WallpaperDescription](https://developer.android.com/reference/android/app/wallpaper/WallpaperDescription), [WallpaperInstance](https://developer.android.com/reference/android/app/wallpaper/WallpaperInstance), [QPR1 Live Effects coverage](https://9to5google.com/2025/06/10/android-16-qpr1-beta-2-adds-live-effects-section-to-wallpaper-picker/), [QPR2 lock-screen widgets](https://www.androidauthority.com/lock-screen-widgets-on-phones-android-16-qpr2-3589668/), [Desktop Mode](https://android-developers.googleblog.com/2026/03/android-devices-extend-seamlessly-to.html).
- Android 17 — [release notes](https://developer.android.com/about/versions/17/release-notes), [overview](https://developer.android.com/about/versions/17), [Beta 1](https://android-developers.googleblog.com/2026/02/the-first-beta-of-android-17.html), [Beta 4](https://android-developers.googleblog.com/2026/04/the-fourth-beta-of-android-17.html).
- Performance Class — [docs](https://developer.android.com/topic/performance/performance-class).
- Ultra HDR — [display docs](https://developer.android.com/media/grow/ultra-hdr/display).
- AGSL — [official guide](https://developer.android.com/develop/ui/views/graphics/agsl/using-agsl), [Compose patterns Medium](https://medium.com/androiddevelopers/agsl-made-in-the-shade-r-7d06d14fe02a).
- Wear OS Watch Face — [Format docs](https://developer.android.com/training/wearables/wff), [Watch Face Push](https://developer.android.com/training/wearables/watch-face-push), [Phone app side](https://developer.android.com/training/wearables/watch-face-push/phone-app), [Androidify case study](https://android-developers.googleblog.com/2025/12/bringing-androidify-to-wear-os-with.html), [Watch faces what's new I/O '25](https://android-developers.googleblog.com/2025/05/whats-new-in-watch-faces.html).
- ML Kit — [Subject Segmentation Android](https://developers.google.com/ml-kit/vision/subject-segmentation/android), [Selfie Segmentation Android](https://developers.google.com/ml-kit/vision/selfie-segmentation/android), [GenAI APIs](https://developers.google.com/ml-kit/genai).
- Gemini Nano / Firebase AI Logic — [docs](https://developer.android.com/ai/gemini-nano), [Hybrid Inference blog](https://android-developers.googleblog.com/2026/04/Hybrid-inference-and-new-AI-models-are-coming-to-Android.html).
- Material You / Monet — [dynamic colors](https://developer.android.com/develop/ui/views/theming/dynamic-colors), [Material 3 Expressive Android Authority](https://www.androidauthority.com/google-material-3-expressive-features-changes-availability-supported-devices-3556392/), [Sid Patil's Monet internals](https://siddroid.com/post/android/chasing-monet-inside-the-android-framework/), [AOSP material display source](https://source.android.com/docs/core/display/material).
- Photo Picker — [Android 14 docs](https://developer.android.com/about/versions/14/changes/partial-photo-video-access).
- Predictive back — [docs](https://developer.android.com/develop/ui/compose/system/predictive-back).
- Compose Adaptive Layouts — [1.2 beta blog](https://android-developers.googleblog.com/2025/09/unfold-new-possibilities-with-compose-adaptive-layouts-1-2-beta.html).
- Compose Strong Skipping — [docs](https://developer.android.com/develop/ui/compose/performance/stability/strongskipping).
- R8 keep rules — [blog](https://android-developers.googleblog.com/2025/11/configure-and-troubleshoot-r8-keep-rules.html).
- AGP — [9.0](https://developer.android.com/build/releases/agp-9-0-0-release-notes), [9.1](https://developer.android.com/build/releases/agp-9-1-0-release-notes), [9.2](https://developer.android.com/build/releases/agp-9-2-0-release-notes).
- Gradle — [9 whats-new](https://gradle.org/whats-new/gradle-9/), [release notes](https://docs.gradle.org/current/release-notes.html).
- Kotlin — [2.3.20](https://kotlinlang.org/docs/whatsnew2320.html), [2.3](https://kotlinlang.org/docs/whatsnew23.html), [2.2.0 announce](https://blog.jetbrains.com/kotlin/2025/06/kotlin-2-2-0-released/).
- KotlinConf 2025 — [JetBrains recap](https://blog.jetbrains.com/kotlin/2025/05/kotlinconf-2025-language-features-ai-powered-development-and-kotlin-multiplatform/).
- Compose updates — [Apr 2026 blog](https://android-developers.googleblog.com/2026/04/jetpack-compose-april-2026-updates.html), [Material 3 release page](https://developer.android.com/jetpack/androidx/releases/compose-material3), [Dec 2025 whats-new](https://android-developers.googleblog.com/2025/12/whats-new-in-jetpack-compose-december.html).
- Dagger / Hilt — [2.59 release](https://github.com/google/dagger/releases/tag/dagger-2.59).
- Room — [release page](https://developer.android.com/jetpack/androidx/releases/room), [Room 3.0 announce](https://android-developers.googleblog.com/2026/03/room-30-modernizing-room.html).
- Retrofit 3 — [discussion #4379](https://github.com/square/retrofit/discussions/4379).
- OkHttp — [CHANGELOG](https://github.com/square/okhttp/blob/master/CHANGELOG.md), [Snyk security](https://security.snyk.io/package/maven/com.squareup.okhttp3%3Aokhttp).
- Coil 3 — [upgrade guide](https://coil-kt.github.io/coil/upgrading_to_coil3/), [CHANGELOG](https://github.com/coil-kt/coil/blob/main/CHANGELOG.md).
- Media3 — [release page](https://developer.android.com/jetpack/androidx/releases/media3), [1.6.0 blog](https://android-developers.googleblog.com/2025/03/media3-1-6-0-is-now-available.html), [1.8.0 whats-new](https://medium.com/google-exoplayer/media3-1-8-0-whats-new-b857435651b9), [1.9.0 whats-new](https://android-developers.googleblog.com/2025/12/media3-190-whats-new.html).
- Navigation Compose — [release page](https://developer.android.com/jetpack/androidx/releases/navigation), [2.9 breakdown Medium](https://medium.com/@androidlab/androidx-navigation-2-9-6-complete-feature-breakdown-4b09ccd637dd).
- Lifecycle — [release page](https://developer.android.com/jetpack/androidx/releases/lifecycle).
- Coroutines — [releases](https://github.com/Kotlin/kotlinx.coroutines/releases).
- Glance — [release page](https://developer.android.com/jetpack/androidx/releases/glance).
- Paging — [release page](https://developer.android.com/jetpack/androidx/releases/paging).
- DataStore — [release page](https://developer.android.com/jetpack/androidx/releases/datastore).
- Firebase — [Android release notes](https://firebase.google.com/support/release-notes/android).
- NewPipeExtractor — [releases](https://github.com/TeamNewPipe/NewPipeExtractor/releases).
- youtubedl-android — [releases](https://github.com/yausername/youtubedl-android/releases).
- Moshi — [CHANGELOG](https://github.com/square/moshi/blob/master/CHANGELOG.md).
- ZXing — [releases](https://github.com/zxing/zxing/releases).
- Security bulletins — [AOSP December 2025](https://source.android.com/docs/security/bulletin/2025-12-01), [SOCPrime CVE-2025-48572/-48633](https://socprime.com/blog/cve-2025-48633-and-cve-2025-48572-vulnerabilities/).
- NewPipe continuity risk — [PiunikaWeb March 2026](https://piunikaweb.com/2026/03/09/newpipe-certified-android-devices-warning/).
- AV1 install base — [Meta engineering analysis](https://engineering.fb.com/2025/09/24/video-engineering/video-streaming-with-av1-video-codec-mobile-devices-meta-white-paper/).
- JPEG XL state — [XDA coverage](https://www.xda-developers.com/jpeg-xl-best-image-format-that-nobodys-using/).
- Color management — [AOSP color-mgmt](https://source.android.com/docs/core/display/color-mgmt).
- xHE-AAC — [Wikipedia USAC](https://en.wikipedia.org/wiki/Unified_Speech_and_Audio_Coding).
- LearnOpenGLES live wallpaper RGB_565 banding — [article](https://www.learnopengles.com/how-to-use-opengl-es-2-in-an-android-live-wallpaper/).
- WallpaperService Engine docs — [reference](https://developer.android.com/reference/android/service/wallpaper/WallpaperService).
- AudioRouting / in-band ringing — [XDA Android Pie in-band ringtones](https://www.xda-developers.com/android-pie-bluetooth-in-band-ringtones-default/).

---

## Appendix D — Community Signal Sources

- Hacker News: [AIWP context-aware wallpapers Show HN](https://news.ycombinator.com/item?id=38418254); [Open-source GitHub repos wallpaper gallery](https://news.ycombinator.com/item?id=46411074); [Wallpaper that grows as you ship](https://news.ycombinator.com/item?id=46692793).
- Android Central — [Zedge complaints thread #979866](https://forums.androidcentral.com/threads/zedge-harmful.979866/page-2); [Bluetooth ringtone routing gap](https://forums.androidcentral.com/threads/can-i-get-phones-ringtone-in-bluetooth-headset.1005511/).
- XDA — [Tasker live wallpaper Bluetooth](https://xdaforums.com/t/tasker-to-change-to-a-live-wallpaper.3758710/); [Sound Pack Giant Audio collection](https://xdaforums.com/t/sound-pack-alarms-ringtones-notifications-ui-giant-audio-pack-collection.4369843/); [Android 14 independent lock-screen live wallpaper](https://www.xda-developers.com/android-14-independent-lock-screen-live-wallpaper/); [Wear OS custom sounds break](https://xdaforums.com/t/custom-notification-sounds-and-ringtones-no-longer-working-after-upgrading-to-the-latest-version-of-wearos.4531515/); [Android Pie Bluetooth in-band](https://www.xda-developers.com/android-pie-bluetooth-in-band-ringtones-default/).
- F-Droid — [Ringtone Maker forum thread #22600](https://forum.f-droid.org/t/ringtone-maker-app/22600); [Inclusion How-To](https://f-droid.org/docs/Inclusion_How-To/); [Reproducible Builds docs](https://f-droid.org/en/docs/Reproducible_Builds/); [2024 retrospective](https://f-droid.org/2025/01/21/a-look-back-at-2024-f-droids-progress-and-whats-coming-in-2025.html); [2025 retrospective](https://f-droid.org/en/2026/01/23/fdroid-in-2025-strengthening-our-foundations-in-a-changing-mobile-landscape.html); [reproducible builds blog](https://f-droid.org/en/2025/05/21/making-reproducible-builds-visible.html); [client 1.19 auto-install](https://f-droid.org/2024/02/01/twif.html); [AlwaysOn FOSS package](https://f-droid.org/en/packages/io.github.domi04151309.alwayson/); [Suntimes package](https://f-droid.org/packages/com.forrestguice.suntimeswidget/); [WallYou package](https://f-droid.org/packages/com.bnyro.wallpaper/); [Ringdroid archive](https://f-droid.org/packages/org.thayyil.ringdroid/); [Paperize package](https://f-droid.org/packages/com.anthonyla.paperize/).
- Reddit / community curation — [Reddit Favorites — Backdrops on AI slop](https://redditfavorites.com/android_apps/backdrops-wallpapers); [DroidViews smart wallpaper](https://www.droidviews.com/automatic-wallpaper-change-contextually-with-smart-wallpaper/); [MakeTechEasier different wallpaper per page](https://maketecheasier.com/add-different-wallpaper-android-home-screen/).
- Tasker — [plugins intro](https://tasker.joaoapps.com/plugins-intro.html); [CalendarTask plugin](https://www.appbrain.com/app/calendartask/com.balda.calendartask).
- Muzei — [API docs](https://api.muzei.co/), [Ian Lake Muzei 3.0 Medium](https://medium.com/muzei/announcing-muzei-live-wallpaper-3-0-d167dd5795a4), [Changelog wiki](https://github.com/muzei/muzei/wiki/Changelog), [Muzei API source on GitHub](https://github.com/muzei/muzei/blob/main/muzei-api/src/main/java/com/google/android/apps/muzei/api/provider/MuzeiArtProvider.java).
- KWGT docs — [official downloads](https://docs.kustom.rocks/docs/downloads/download-kwgt/).
- Awesome lists — [awesome-android-livewallpaper](https://github.com/vvolas/Awesome-Live-Wallpaper); [awesome-kotlin (Heapy)](https://github.com/Heapy/awesome-kotlin); [jetpack-compose-awesome](https://github.com/jetpack-compose/jetpack-compose-awesome); [awesome-android-ui (wasabeef)](https://github.com/wasabeef/awesome-android-ui); [awesome-android (JStumpp)](https://github.com/JStumpp/awesome-android); [awesome-wear-os](https://github.com/WearOSCommunity/awesome-wear-os); [google/watchface validator](https://github.com/google/watchface).
- Distribution — [Per-ABI APK splits Medium](https://cdmunoz.medium.com/goodbye-giant-apk-how-we-went-from-186-mb-to-62-mb-with-split-per-abi-and-three-lines-in-ci-673dd71dbdcb); [AAB vs APK overview](https://www.appsonair.com/blogs/apk-vs-aab---what-really-changes-internally-beyond-the-marketing); [Privacy Guides obtaining apps](https://www.privacyguides.org/en/android/obtaining-apps/); [Obtainium GitHub](https://github.com/ImranR98/Obtainium); [IzzyOnDroid index](https://apt.izzysoft.de/fdroid/index/info).
- ML / depth references — [Qualcomm Depth-Anything-V2 TFLite](https://huggingface.co/qualcomm/Depth-Anything-V2); [Qualcomm Stable Diffusion <1s blog](https://www.qualcomm.com/news/onq/2024/02/worlds-first-on-device-demonstration-of-stable-diffusion-on-android); [arXiv 2503.14868 Efficient Personalization of Quantized Diffusion](https://arxiv.org/abs/2503.14868); [arXiv 2412.06661 Efficiency Meets Fidelity](https://arxiv.org/abs/2412.06661); [arXiv 2306.02316 Temporal Dynamic Quantization](https://arxiv.org/abs/2306.02316); [MediaPipe Image Segmenter Android](https://ai.google.dev/edge/mediapipe/solutions/vision/image_segmenter/android).
- Battery — [battery study](https://thebatterytips.com/battery-specifications/do-live-wallpapers-take-up-battery/).
- Pixel features — [Generative AI wallpapers Android Authority](https://www.androidauthority.com/generative-ai-wallpapers-3336181/); [Pixel 10 unlock-change generative wallpaper](https://store.google.com/intl/en/ideas/articles/september-pixel-drop-personalization/).
- Public Wallhaven / Pexels — [Wallhaven help/api](https://wallhaven.cc/help/api); [Pexels API ToS replication article](https://help.pexels.com/hc/en-us/articles/4405588861721).
- Audius — [docs.audius.org](https://docs.audius.org/api/).
- Freesound intro / CC0 — [Creative Commons / Freesound](https://opensource.creativecommons.org/blog/entries/freesound-intro/); [Freesound APIv2 docs](https://freesound.org/docs/api/resources_apiv2.html); [Freesound developer auth](https://freesound.org/help/developers/).
- Spotify color extraction — [Medium pipeline analysis](https://medium.com/@shanmugashree3/how-spotify-creates-those-stunning-backdrops-that-match-every-song-playlist-00fe13eab033).
- Reproducible builds / palette references — [Sid Patil Monet](https://siddroid.com/post/android/chasing-monet-inside-the-android-framework/); [Palette generator experiment](https://github.com/irisxu02/palette-generator-experiment); [Dev.to dynamic color Compose](https://dev.to/myougatheaxo/dynamic-color-material-you-in-compose-wallpaper-based-theming-325j).
- ML Kit issues from existing pitfall log — [#137](https://github.com/googlesamples/mlkit/issues/137), [#386](https://github.com/googlesamples/mlkit/issues/386), [#436](https://github.com/googlesamples/mlkit/issues/436).
- Yt-dlp legal context — [audioutils blog](https://audioutils.com/blog/is-yt-dlp-legal).
- Health Connect — [docs](https://developer.android.com/health-and-fitness/guides/health-connect).
- Channels API — [Compose notifications](https://developer.android.com/develop/ui/compose/notifications/channels).
- Talkback caller-ID — [Google Accessibility](https://support.google.com/accessibility/android/answer/6006564).
- Sunrise alarm references — [yuriykulikov/AlarmClock GitHub](https://github.com/yuriykulikov/AlarmClock).
- Custom ringtones Android UX — [9to5Google March 2024 contacts](https://9to5google.com/2024/03/20/google-contacts-custom-ringtones-android/).
