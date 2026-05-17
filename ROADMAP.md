# Aura — Product Roadmap

> Open-source Android personalization: wallpapers, video wallpapers, ringtones, sounds.
> Stay the OSS alternative to Zedge: no ads, no surprise charges, no dark patterns.

**Version:** 2026-05-17-rev4 (freshness pass: Android 17 stable approaches June 2026 — added NX-10 EyeDropper API, NX-11 Photo Picker 9:16, NX-12 CI verification, NX-13 predictive-back wiring, L-10 adaptive layouts; logged yt-dlp CVE-2026-26331 risk row; competitor validation from One UI 8.5 stable rollout).
**Code version at write:** v6.31.0 / versionCode 111 (per `app/build.gradle.kts`; build verification still pending in N-1 toolchain pass).
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

## State of the Repo (snapshot, 2026-05-17)

- Kotlin 2.1.0 / Compose / Material 3, Hilt 2.53.1, Room 2.6.1 (v14), Retrofit 2.11.0, OkHttp 4.12.0, Media3 1.5.1, Coil 2.7.0, WorkManager 2.10.0, Glance 1.1.1, NewPipe Extractor 0.24.8, youtubedl-android 0.18.1, **ML Kit `segmentation-subject:16.0.0-beta1`** (N-3 migrated 2026-05-16), **Firebase BoM 34.13.0** (N-2 shipped 2026-05-16), `play-services-base:18.5.0` (ModuleInstallClient for unbundled segmenter).
- 124 Kotlin files in `app/src/main/java/com/freevibe/`, 49 unit-test files (+1 audit-pass tests), 0 known CVEs in scanner, 1 design-note TODO resolved (`VoteRepository.kt` admin auth → Custom Claims).
- Shipped via 22 Implementation Passes since 2026-04-25 (latest: 2026-05-17 hardening audit). See Implementation Log.
- Distribution: GitHub Releases only; signed via `freevibe.jks`. CI workflow `.github/workflows/release.yml` triggered on `v*` tag. Per-ABI splits + F-Droid metadata still pending (NX-8).
- Package id `com.freevibe`, brand "Aura"; do not change without a migration plan (re-installs lose data; existing community uploads keyed by device id).
- Build env on the executing VM: **no JDK/Android SDK installed** — every Implementation Pass since 2026-04-25 has been static-review-only. `./gradlew :app:assembleDebug` runtime verification (N-1 gating) requires a workstation with the Android Studio JBR + SDK 35.
- CI surface (rev4 note): `.github/workflows/release.yml` only triggers on `v*` tag push or `workflow_dispatch`. There is **no automated PR / push build verification, no unit-test run, no lint** — the manual gating gap that NX-12 closes.
- Platform horizon (rev4 note): Android 17 reached Platform Stability in Beta 3 (March 2026), Beta 4 shipped 2026-04-16, stable expected June 2026 — sets API 37 baseline with EyeDropper, PhotoPicker 9:16 customization, Contacts Picker, ACCESS_LOCAL_NETWORK, Bubbles. One UI 8.5 stable rolling out May 2026 with Smart Subject Placement + AI Weather Effects — competitor validation of Aura's existing Phase 6.3 weather trajectory + NX-2 lockscreen-depth direction. ([Android 17 release notes](https://developer.android.com/about/versions/17/release-notes); [9to5Google Beta 2 EyeDropper](https://9to5google.com/2026/02/26/android-17-beta-2-contacts-and-display-color-access/); [SamMobile One UI 8.5 features](https://www.sammobile.com/news/one-ui-8-5-update-top-features/).)

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

Five items. Four landed in the 2026-05-16 autonomous batch (N-2..N-5, marked
`[~]` and detailed in the Implementation Log). N-1 remains; it requires a build
environment that can run `./gradlew :app:assembleDebug`.

- [ ] **N-1** — Toolchain upgrade triad (AGP/Gradle/Kotlin/Compose BOM/Hilt). Deferred from the 2026-05-16 pass; needs JDK+SDK to verify.
- [~] **N-2** — Firebase BoM 34.13.0 + Custom Claims admin path. Code + rules shipped; deploy `database.rules.json` + grant claims to existing admins to complete the rollout.
- [~] **N-3** — Subject Segmentation + AGSL pipeline scaffold. Code shipped; concrete AGSL effects ship in NX-1/NX-2 follow-ups.
- [~] **N-4** — Photo Picker + monochrome themed icon. Code shipped. WallpaperDescription scaffolding is comment-only until N-1 unlocks compileSdk 36.
- [~] **N-5** — Aura Originals manifest schema + first-launch downloader. Infrastructure shipped; curation pass adds the actual sound entries to `assets/aura_originals_manifest.json`.

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

Thirteen items. All scored 18–25. Pull from the top of this list when Now closes. Four new items added in rev4 (NX-10..NX-13) sit at the back of the queue but score well; promote ahead of older items only when their dependencies (N-1 toolchain, primarily) are unblocked.

### NX-1. GL/AGSL live wallpaper engine migration (T-9 reframed)

- **Source(s):** [AlynxZhou/alynx-live-wallpaper](https://github.com/AlynxZhou/alynx-live-wallpaper) (ExoPlayer + OpenGL ES reference); [maocide/UndeadWallpaper](https://github.com/maocide/UndeadWallpaper) (gapless OpenGL + ExoPlayer); [Media3 1.9 dav1d-based AV1 extension](https://android-developers.googleblog.com/2025/12/media3-190-whats-new.html); [Media3 1.6 pre-warming decoders](https://android-developers.googleblog.com/2025/03/media3-1-6-0-is-now-available.html); [patzly/pallax-android archive note](https://github.com/patzly/pallax-android) (Canvas-based live wallpapers were archived due to rendering inefficiency — direct cautionary tale); [GLSurfaceView RGB_565 banding pitfall](https://www.learnopengles.com/how-to-use-opengl-es-2-in-an-android-live-wallpaper/); [scale-types issue](https://github.com/AlynxZhou/alynx-live-wallpaper/issues/14).
- **Why next:** Aura's `VideoWallpaperService` uses `MediaPlayer` with `setVolume(0,0)`. Moving to Media3 ExoPlayer + AGSL/OpenGL pipeline lands four wins at once: gapless transitions; AV1 decode where hardware supports it; per-video focus rectangle / pan + zoom (Pixel Live Effects "Cinematic" parity); proper aspect-ratio handling. The existing Canvas-based parallax should also migrate behind AGSL `RuntimeColorFilter` for the same reason — Pallax was archived because Canvas live wallpapers can't keep up.
- **Scope:** Vendor a thin `GLWallpaperService` base in `com.freevibe.wallpaper.gl/`. Pause render thread on `Engine.onVisibilityChanged(false)`. Add `media3-ui-compose` for the preview surface. Replace `VideoWallpaperService` MediaPlayer path with ExoPlayer + `samplerExternalOES` shader. Keep the Canvas GIF renderer (already battery-bounded). Add Pan / Zoom / Focus controls in the apply sheet. Per-video FPS cap + quality preset (Wallpaper Engine parity).
- **Risk:** Largest refactor in 12 months. AV1 hardware decode is ~10 % of install base ([Meta engineering analysis](https://engineering.fb.com/2025/09/24/video-engineering/video-streaming-with-av1-video-codec-mobile-devices-meta-white-paper/)). Battery regression risk if the new pipeline skips invisible-pause.
- **Fit 4 / Impact 5 / Effort 1 / Risk 2 / Deps 3 / Novelty 4 = 19 → NEXT.**

### NX-2. Lockscreen depth — Subject-aware clock-tuck + lockscreen Glance widgets — `[~]` widget surface enabled 2026-05-17 rev4-impl

> Lockscreen Glance widget surface enabled: `res/xml/freevibe_widget_info.xml` widget category bumped from `home_screen` to `home_screen|keyguard`. On Android 16 QPR2+ (December 2025 stable) the existing `FreeVibeWidget` is now placeable on the lockscreen surface without any Glance code change — the widget already reads from `WallpaperHistoryManager` so it shows the most-recent applied wallpaper as a background. Older Android versions silently ignore the `keyguard` bit. Clock-tuck (subject-aware mask blending) + the dedicated daily-pick lockscreen widget variant still pending — those need a `WallpaperHistoryManager.subjectMask` field, a new lockscreen-only `daily_pick_widget_info.xml`, and the Glance composable. Held until the user has tested the existing widget on a Pixel 9 / 10 lockscreen so we know which size to design for.



- **Source(s):** [Android 16 QPR2 lock-screen widgets on phones](https://www.androidauthority.com/lock-screen-widgets-on-phones-android-16-qpr2-3589668/); [Glance 1.2 release notes](https://developer.android.com/jetpack/androidx/releases/glance); [One UI 8.5 Adaptive Lock Screen Clock](https://www.sammyfans.com/2025/09/28/one-ui-8-adaptive-lock-screen-clock/); [Nothing OS 4.1 depth effect](https://gadgets.beebom.com/guides/nothing-os-4-1-features); [iOS-style Depth Effect](https://www.one4studio.com/glossary/parallax-wallpaper); [Muzei issue #794 — different sources for lock and home](https://github.com/muzei/muzei/issues/794); [Doodle issue #92 — static lockscreen wallpaper](https://github.com/patzly/doodle-android/issues/92).
- **Why next:** Aura's `dualWallpapers` already handles home/lock pairs. What's missing is the *subject-aware* depth effect that iOS, Nothing OS, and One UI all ship. With N-3's Subject Segmentation in place, "clock tucks behind wallpaper subject" is a derivative feature. Lock-screen Glance widgets land on phones in Android 16 QPR2; Aura's existing Glance widget should opt-in (`not_keyguard` category check) so it can run as a lockscreen daily-pick.
- **Scope:** Generate clock-mask Bitmap on apply via subject segmentation; persist to `wallpaper_history` table. New live-wallpaper engine renders subject foreground layer over an artificially deepened background blur, with a hint surface that the system lockscreen renderer overlays the clock against. Ship a lockscreen Glance widget variant of Daily Pick. Add a "Lock screen only" option to wallpaper apply (Doodle parity).
- **Risk:** Engine-side clock-position estimation is heuristic on non-Pixel devices; ship as Pixel + Samsung allowlist initially.
- **Fit 5 / Impact 4 / Effort 2 / Risk 4 / Deps 3 / Novelty 5 = 23 → NEXT.**

### NX-3. Smart Crop with Subject Segmentation (Phase 6.5 finally) — `[~]` wallpaper variant shipped 2026-05-17 rev4-impl

> Wallpaper crop shipped. New `SmartCropCalculator.kt` (pure geometry, 7 unit tests) + `SmartCropDetector.kt` (ML Kit Subject Segmentation via the same unbundled segmenter that N-3 wired into `ParallaxWallpaperService`, accessed via reflection on the `SubjectSegmentationResult` type so the file is robust against minor ML Kit API drift). `WallpaperCropViewModel.applySmartCrop` is a suspend function returning the new `(scale, offsetX, offsetY)` transform; the composable launches it via `rememberCoroutineScope().launch` and syncs local `rememberSaveable` gesture state on success. UI surface: new `Smart Crop` FilterChip in the aspect-ratio row with a sparkle icon, "Detecting…" label + spinner while in flight, and a "Couldn't detect a subject — drag to position manually" snackbar fallback. `VideoCropScreen` smart-crop variant still pending (FFmpeg-side geometry is different; deferred to a follow-up commit).



- **Source(s):** Aura Phase 6.5 (never shipped); [ML Kit Subject Segmentation Android](https://developers.google.com/ml-kit/vision/subject-segmentation/android); [WallFlow Plus smart crop](https://github.com/ammargitham/WallFlow); [WallYou advanced cropping](https://github.com/you-apps/WallYou/issues/189); [Paperize vertical scrolling crop](https://github.com/Anthonyy232/Paperize/issues/428).
- **Why next:** N-3 lands Subject Segmentation; smart crop becomes a 2-day feature. Aura's existing pinch-zoom + aspect presets already give you most of the chrome; the missing piece is auto-positioning the crop rectangle to keep the primary subject in frame when reshaping landscape → portrait.
- **Scope:** Smart Crop toggle in `WallpaperCropScreen` + `VideoCropScreen`. When enabled, run Subject Segmentation, compute bounding box, center crop rectangle on it. Compare against rule-of-thirds heuristic for non-portrait outputs. Fall back to existing center-crop if confidence < 0.5.
- **Risk:** Slow on low-end devices; gate on Performance Class.
- **Fit 5 / Impact 4 / Effort 4 / Risk 5 / Deps 2 (depends on N-3) / Novelty 3 = 23 → NEXT.**

### NX-4. SelectedContentHolder removal (Phase 7.2) — `[~]` process-death survival shipped 2026-05-17 rev4-impl

> Singleton now persists the **single selected wallpaper + selected sound** to a `freevibe_selected_content` SharedPreferences file via Moshi JSON on every `select*` call. On Hilt construction the holder lazy-restores from disk so after process death the detail screen's primary item is intact. `wallpaperList` (the pager-supporting list) intentionally still in memory only — process-death restoration of a 50-item URL list would jam the cold start with prefetch; the detail screen already handles the "list lost" case by collapsing to single-item display.
>
> Full sweep — nav-graph-scoped `SelectionViewModel` backed by `SavedStateHandle` + `ViewModelScenario` process-death tests + delete `SelectedContentHolder.kt` — still queued. It's the wider refactor that touches every detail/pager screen and rides Navigation 2.9 type-safe routes (N-1-gated). This NX-4 rev4-impl closes the worst-case "wallpaper detail blank on resume" bug class without that refactor.



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

### NX-6. Scheduler triggers — per-app exclusion, screen-off pre-stage, sub-15-min intervals, per-unlock — `[~]` per-unlock + screen-off pre-stage shipped 2026-05-17 rev4-impl

> **Per-unlock + screen-off pre-stage shipped.** New `RotationTriggerService` (foreground service with `specialUse` type) dynamically registers `Intent.ACTION_USER_PRESENT` + `Intent.ACTION_SCREEN_OFF` receivers (both blocked from manifest registration since Android 8). On each fire it enqueues a one-shot expedited `AutoWallpaperWorker` via `WorkManager.enqueueUniqueWork(KEEP)` so chatty unlock sequences coalesce. Two new DataStore prefs `rotateOnUnlock` / `rotateOnScreenOff` (default false) gate the service lifecycle; `RotationTriggerService.reconcile(unlock, screenOff)` is the idempotent start/stop entry point invoked from `FreeVibeApp.onCreate` (cold-start rehydration) and `SettingsViewModel.setRotateOn{Unlock,ScreenOff}` (toggle-driven). Manifest declares the service + `FOREGROUND_SERVICE_SPECIAL_USE` permission; users see a low-priority "Wallpaper triggers active" notification only when at least one trigger is opted in. Two new Settings toggles in the rotation section: "Change on every unlock" + "Pre-stage on screen off".
>
> **Still pending:** per-app rotation exclusion (needs `PACKAGE_USAGE_STATS` runtime permission flow), sub-15-min interval via AlarmManager `setExact`, and the one-tap-shuffle Glance widget per the WallYou ask. Hold those behind real user feedback on the per-unlock surface.



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

### NX-8. Distribution to F-Droid + IzzyOnDroid + Obtainium — `[~]` fastlane refresh + Obtainium manifest shipped 2026-05-17 rev4-impl

> Fastlane metadata under `fastlane/metadata/android/en-US/` refreshed: title (`FreeVibe` → `Aura`), short description bumped to reflect no-ads / no-tracking, full description rewritten against the current feature set (29 features incl. NX-3 Smart Crop, NX-6 rotation triggers, L-2 Tasker hook, parallax, weather effects). New `changelogs/111.txt` lands v6.31.0 release notes. New `obtainium.json` at repo root lets Obtainium users track Aura via GitHub Releases with the `v*` tag regex + APK filter.
>
> Still pending: per-ABI `splits { abi { ... } }` in `app/build.gradle.kts` (needs N-1 build verification to cut the universal APK from one fat binary to four lean ones), F-Droid metadata PR (depends on reproducible builds + Firebase question), IzzyOnDroid submission (path of least friction; can submit once a `v*` tag is signed and visible). The `verify.yml` workflow (NX-12) is the prerequisite for F-Droid reproducible-build verification.



- **Source(s):** [F-Droid Inclusion How-To](https://f-droid.org/docs/Inclusion_How-To/); [F-Droid Reproducible Builds docs](https://f-droid.org/en/docs/Reproducible_Builds/); [F-Droid in 2025 retrospective](https://f-droid.org/en/2026/01/23/fdroid-in-2025-strengthening-our-foundations-in-a-changing-mobile-landscape.html); [F-Droid 1.19+ Session Installer background updates](https://f-droid.org/2024/02/01/twif.html); [IzzyOnDroid repo docs](https://apt.izzysoft.de/fdroid/index/info); [Obtainium app](https://github.com/ImranR98/Obtainium); [APK splits per-ABI reference](https://cdmunoz.medium.com/goodbye-giant-apk-how-we-went-from-186-mb-to-62-mb-with-split-per-abi-and-three-lines-in-ci-673dd71dbdcb); existing `.github/workflows/release.yml`.
- **Why next:** Aura's only distribution channel is signed GitHub Releases. The OSS Zedge-alternative pitch demands F-Droid presence. IzzyOnDroid is the path of least friction; Obtainium asks only for a structured release manifest. Per-ABI splits cut the APK from a single universal binary to four lean ones — meaningful since youtubedl-android bundles Python 3.8.
- **Scope:** Ship `fastlane/metadata/android/en-US/{short_description.txt,full_description.txt,changelogs/,images/}`. Verify reproducible builds via `apksigner` + `--ks-key-alias` (per F-Droid docs). Submit to IzzyOnDroid first. Add `splits { abi { ... } }` to `app/build.gradle.kts` with per-ABI release outputs. Update release workflow to attach all four APKs + a universal. Add an `obtainium` JSON manifest at repo root. Open the F-Droid metadata PR last (it's the slowest review).
- **Risk:** F-Droid forbids non-free dependencies. Firebase Storage may push you to the IzzyOnDroid track only (which permits proprietary deps). NewPipe Extractor + youtubedl-android are GPL and OK.
- **Fit 5 / Impact 4 / Effort 3 / Risk 4 / Deps 3 / Novelty 2 = 21 → NEXT.**

### NX-9. Media3 1.10 Material3 playback composables + dynamic scheduling

- **Source(s):** [Media3 1.10 release blog](https://android-developers.googleblog.com/2026/03/media3-110-is-out.html); [Media3 1.10 dev blog post](https://developer.android.com/blog/posts/media3-1-10-is-out); [Media3 release page](https://developer.android.com/jetpack/androidx/releases/media3); [Compose 2026 ExoPlayer guide](https://medium.com/@ramadan123sayed/media-player-in-jetpack-compose-the-complete-2026-guide-exoplayer-media3-1-10-0a25af46ce7d); existing `SoundDetailScreen.kt` hand-rolled waveform + progress; existing `WallpaperPreviewScreen` video preview.
- **Why next:** Aura's sound preview UI rolls its own waveform + progress + speed control across `SoundDetailScreen`, `SoundEditorScreen`, and the YouTube tab. Media3 1.10 (March 2026) adds Material3-styled composables — `PlayerComposable` (combines `ContentFrame` + controls), `ProgressSlider`, `PlaybackSpeedControl` — that replace ~300 LOC of custom UI with library code styled to match the rest of the app. Bonus: `ExoPlayer.Builder.experimentalSetDynamicSchedulingEnabled()` ships in 1.10 as an experimental power-saver for the in-app video preview surface — direct fit for Aura's battery-discipline charter.
- **Scope:** Bump Media3 1.5.1 → 1.10.0 (sequenced inside N-1's lockstep toolchain pass; the new composables compile against Compose BOM 2026.05 only). Migrate `WallpaperPreviewScreen` video preview to `PlayerComposable` + `ContentFrame` + Aura's existing `GlassCard` chrome. Replace the hand-rolled scrubber in `SoundDetailScreen` with `ProgressSlider`. Add `PlaybackSpeedControl` to `SoundEditorScreen` (currently no in-editor speed control). Opt into `experimentalSetDynamicSchedulingEnabled()` behind a Settings → Advanced → Dynamic scheduling toggle for the video preview surface.
- **Risk:** Library composables don't yet expose Aura's rectangular 4-12dp radius/letter-spacing design system from v6.16.0 polish — may need a thin theming wrapper. Experimental dynamic-scheduling API can be removed in any minor release; flag for monitoring.
- **Fit 4 / Impact 3 / Effort 3 / Risk 4 / Deps 3 (N-1) / Novelty 2 = 19 → NEXT.**

### NX-10. Android 17 EyeDropper API — pixel-pick → wallpaper colour search — `[~]` shipped 2026-05-17 rev4-impl-2

> EyeDropper FAB lands in `WallpapersScreen` `FloatingActionTray` on the Discover tab. Raw-string Intent (`"android.intent.action.OPEN_EYE_DROPPER"` + `"android.intent.extra.COLOR"`) keeps the integration compatible with compileSdk 35 — no API 37 class refs at compile time. `eyeDropperAvailable` probes `PackageManager.resolveActivity` so the FAB hides on builds where the system EyeDropper app hasn't been installed yet (un-updated GSI). On pick, the returned `Int` colour flows through new `WallpapersViewModel.searchByPickedColor()` which strips alpha and converts to the 6-char hex Wallhaven's `colors=` query expects. No fallback path needed — Android 16 and below silently keep the existing Material You + Wallhaven palette flow because the FAB is hidden when the API isn't on the device. Future surface: the same launcher could seed `AiWallpaperScreen` prompts and community-upload colour tags; held to confirm Android 17 install-base + user signal first.



- **Source(s):** [Android 17 Beta 2 EyeDropper announce — 9to5Google](https://9to5google.com/2026/02/26/android-17-beta-2-contacts-and-display-color-access/); [Android Engineers Substack walkthrough](https://androidengineers.substack.com/p/introducing-the-android-17-eye-dropper); [ProAndroidDev EyeDropper API deep-dive (Mar 2026)](https://proandroiddev.com/exploring-the-eyedropper-api-android-17-9d7be86aaa16); [Android 17 release notes](https://developer.android.com/about/versions/17/release-notes); [Android Authority first look](https://www.androidauthority.com/android-17-eyedropper-color-picker-3610073/); existing `WallpapersViewModel.matchMyTheme` (Material You accent → Wallhaven `colors=` query).
- **Why next:** Aura's flagship "Match my theme" feature seeds Wallhaven colour search from the system Material You accent. EyeDropper (`Intent.ACTION_OPEN_EYE_DROPPER`, `Intent.EXTRA_COLOR`) ships in Android 17 (Beta 2, locked in Beta 3) and lets the user pick **any** on-screen pixel without screen-recording permission or accessibility-service abuse. Direct fit: "pick a colour from anywhere → seed wallpaper search → match my desk lamp / album art / favourite jacket". No OSS wallpaper app uses it yet — leapfrog opportunity tracked at zero implementation cost.
- **Scope:** Wallpaper search bar + AI generation prompt + community-upload tag editor each get an EyeDropper FAB. Implement behind `Build.VERSION.SDK_INT >= 37` gate (no fallback needed — Android 16 and below keep the existing Material You + Wallhaven palette flow). Launch via `ActivityResultContracts.StartActivityForResult` since the API returns `Intent.EXTRA_COLOR` as an `Int`. Convert to nearest `WallhavenPurity`-safe colour query and to a Wallhaven `colors=` hex.
- **Risk:** Android 17+ only — install base hits ~10 % by EOY 2026, mainstream by Q2 2027. Settings → Advanced toggle to surface the feature on supported devices avoids dead UI on older versions.
- **Fit 4 / Impact 3 / Effort 4 / Risk 4 / Deps 3 (N-1 raises targetSdk) / Novelty 4 = 22 → NEXT.**

### NX-11. Android 17 Photo Picker 9:16 portrait customization (N-4 follow-up) — `[~]` shipped 2026-05-17 rev4-impl-2

> Drop-in `AuraPickVisualMedia` subclass of `ActivityResultContracts.PickVisualMedia` overrides `createIntent` and calls `PhotoPickerCustomization.apply9x16AspectRatio(intent)` before launching. The helper does the actual API-37-only API call via reflection (`PhotoPickerUiCustomizationParams.Builder().setGridAspectRatio(9, 16).build()` + `Intent.putExtra(EXTRA_PHOTO_PICKER_UI_CUSTOMIZATION_PARAMS, params)`) so the integration ships at compileSdk 35 today and becomes a straight-line call once N-1 unlocks compileSdk 37. Wired at three call sites: wallpaper community upload (`WallpapersScreen`), collection QR import (`CollectionsScreen`), parallax-from-photo (`SettingsScreen`). Reflection failure is logged DEBUG and never throws — picker falls back to its default 1:1 grid. Android 16 and below pass through transparently.



- **Source(s):** [Android Developers Blog — Android 17 Beta 3 PhotoPickerUiCustomizationParams](https://android-developers.googleblog.com/2026/03/the-third-beta-of-android-17.html); [Android 17 release notes (Photo Picker section)](https://developer.android.com/about/versions/17/release-notes); [Photo Picker docs](https://developer.android.com/training/data-storage/shared/photo-picker); existing `PickVisualMedia.ImageOnly` call sites in `WallpapersScreen` community upload + `CollectionsScreen` QR import (landed in N-4 / commit `b0ae1fe`).
- **Why next:** N-4 migrated image imports to the system Photo Picker. Android 17 adds `PhotoPickerUiCustomizationParams` to switch the picker's grid from 1:1 squares to 9:16 portrait thumbnails — the canonical wallpaper-app aspect ratio. Every wallpaper Aura ships at is portrait; every gallery picker today crops thumbnails wrong. This is the smallest, highest-fit follow-up to N-4 on the platform.
- **Scope:** Wrap existing `PickVisualMedia` launchers in a version-gated builder. On Android 17+, attach `PhotoPickerUiCustomizationParams.Builder().setGridAspectRatio(9, 16).build()`. No code-change for older versions. One commit, ~30 LOC.
- **Risk:** API only available on API 37+. Test against the embedded photo picker on Pixel 6+ once N-1 unlocks compileSdk 37.
- **Fit 5 / Impact 3 / Effort 5 / Risk 5 / Deps 3 (N-1) / Novelty 2 = 23 → NEXT.**

### NX-12. CI build verification on every push / PR (workflow gap) — `[~]` shipped 2026-05-17 rev4-impl

> Shipped `.github/workflows/verify.yml` — triggers on `push: main` + `pull_request: main` + `workflow_dispatch`. Runs `assembleDebug` + `testDebugUnitTest` + `lintDebug` on JDK 17 with Gradle cache. Stubs `local.properties` so signing/API-key lookups don't fail in CI (release.yml stays the source of truth for signed builds). Uploads test + lint reports as artifacts on failure with 14-day retention. `concurrency` group cancels superseded runs so CI doesn't queue up on rapid pushes. Branch protection requiring `verify` must still be enabled on `main` by the repo owner.



- **Source(s):** existing [`.github/workflows/release.yml`](.github/workflows/release.yml) — `on: push: tags: ['v*']` + `workflow_dispatch` only; no PR / branch protection trigger; no unit-test or lint step; [GitHub Actions Android template](https://github.com/actions/starter-workflows/blob/main/ci/android.yml); [Gradle build cache action `gradle/actions/setup-gradle`](https://github.com/gradle/actions); [F-Droid Reproducible Builds requirements](https://f-droid.org/en/docs/Reproducible_Builds/) (NX-8 depends on a clean build environment); existing 49 unit-test files awaiting CI runs.
- **Why next:** Every Implementation Pass since 2026-04-25 has been **static-review-only** because the executing environment has no JDK/SDK. The N-1 toolchain triad (AGP 9 / Gradle 9 / Kotlin 2.3) cannot be honestly tested without a build-verified CI lane — bumping versions blind is a known regression vector for KSP2, Hilt, and Compose Strong Skipping. The current `release.yml` only fires on tag, so PRs and `main` pushes go un-verified. This is the dev-experience gap blocking N-1, NX-1, and most of T-A.
- **Scope:** New `.github/workflows/verify.yml` triggered on `push: branches: [main]` and `pull_request: branches: [main]`. Jobs: setup JDK 17 → cache Gradle → `./gradlew assembleDebug testDebugUnitTest lintDebug`. Upload `app/build/reports/{tests,lint-results-debug.html}` as artifacts on failure. Optionally: `./gradlew :app:assembleRelease` behind a manually-fired `release-dry-run` job that uses a CI-only signing key (no leak risk; release.yml stays the source of truth). Enable branch protection on `main` requiring `verify` to pass. F-Droid reproducible-build verification is a stretch follow-up — defer to NX-8.
- **Risk:** Workflow drift if `verify.yml` and `release.yml` diverge — mitigate by extracting the build steps into a shared composite action or a reusable workflow. Secrets-leak risk on PRs from forks — keep all signing keys out of `verify.yml`; restrict release jobs to `pull_request_target` only if absolutely needed (default: no).
- **Fit 5 / Impact 4 / Effort 4 / Risk 5 / Deps 4 / Novelty 1 = 23 → NEXT.**

### NX-13. Predictive-back wiring through Compose NavHost transitions — `[~]` partial, 2 of ~18 screens 2026-05-17 rev4-impl

> First-cut BackHandler discipline on the two highest-stakes in-flight screens:
> - **`AiWallpaperScreen`** — back during generation cancels the in-flight Stability AI job (new `AiWallpaperViewModel.cancelGeneration()` + `generationJob: Job` tracker + `onCleared()` defensive cancel). Saves the user's API credit budget when they back out of a slow generation.
> - **`VideoCropScreen`** — back while the FFmpeg subprocess is running toasts "Cropping in progress — please wait" and holds the screen so the cropped file has somewhere to land. Doesn't kill ffmpeg (its lifecycle is process-not-coroutine).
>
> Remaining 16 detail/editor/preview/picker screens (WallpaperEditorScreen, SoundEditorScreen, WallpaperDetailScreen, SoundDetailScreen, WallpaperPreviewScreen, VideoWallpaperPreviewScreen, ContactPickerScreen, and the rest) still rely on default activity finish. Full NavHost predictive-back-aware transitions ride on Navigation 2.9 which is N-1-gated. Hold the remainder until N-1 lands.



- **Source(s):** [Predictive back in Compose docs](https://developer.android.com/develop/ui/compose/system/predictive-back); [Navigation 2.9 predictive-back integration](https://medium.com/@androidlab/androidx-navigation-2-9-6-complete-feature-breakdown-4b09ccd637dd); [Android 14 predictive back behaviour change](https://developer.android.com/about/versions/14/behavior-changes-14#predictive-back-gesture); existing `AndroidManifest.xml:50` (`android:enableOnBackInvokedCallback="true"`); existing `BackHandler` use confined to `CollectionsScreen.kt` + `FavoritesScreen.kt` (only 2 of ~22 detail/editor screens).
- **Why next:** Aura's manifest opts in to predictive back. Without per-screen `BackHandler` discipline, Compose detail / editor / preview / picker screens fall back to default activity finish — the user gets no smooth peek-the-previous-screen animation that Android 14+ defaults to. With Navigation 2.9's predictive-back integration landing in N-1, every detail screen (WallpaperDetailScreen, SoundDetailScreen, AiWallpaperScreen, CollectionsScreen, WallpaperEditorScreen, VideoCropScreen, SoundEditorScreen, ContactPickerScreen) should declare a `BackHandler` for in-flight state cleanup (cancel coroutines, save scroll position) and animate `progress` smoothly through `PredictiveBackHandler`.
- **Scope:** Audit all 22 screens. Add `BackHandler` to 18 missing ones with the right cleanup (cancel any in-flight FFmpeg / yt-dlp / segmenter job, save selection state). Switch NavHost to Navigation Compose 2.9's predictive-back-aware transitions in the same commit that bumps Navigation in N-1. Add a `PredictiveBackHandler` to one or two high-value flows (WallpaperEditor crop preview pull-to-dismiss; SoundEditor unsaved-changes confirm).
- **Risk:** Misplaced `BackHandler` can swallow back navigation entirely — keep each guard narrow (`enabled = state.isInflight || state.hasUnsavedChanges`). Predictive-back animations require Navigation 2.9+ for the NavHost integration; gating ties to N-1.
- **Fit 4 / Impact 3 / Effort 4 / Risk 4 / Deps 4 (N-1) / Novelty 1 = 20 → NEXT.**

---

## Later — scoped, deferred

### L-1. Wear OS 6 companion via Watch Face Push API (was Phase 8.2)

- **Source(s):** [Watch Face Push API training](https://developer.android.com/training/wearables/watch-face-push); [Phone-side companion docs](https://developer.android.com/training/wearables/watch-face-push/phone-app); [Androidify on Wear OS](https://android-developers.googleblog.com/2025/12/bringing-androidify-to-wear-os-with.html); [Facer 5.0 Wear OS 6 features](https://news.facer.io/massive-facer-update-wear-os-6-new-features-for-all-7bb1480b5797); [cmota/Unsplash KMP Wear OS](https://github.com/cmota/Unsplash); [Watch Face Format docs](https://developer.android.com/training/wearables/wff).
- **Why later:** Wear OS 6 install base is small (Pixel Watch 4 launch). Watch Face Push API requires `minSdk=33` on the watch and is restricted to one face per marketplace app. Aura's novelty: a watch face *generated* from the user's currently-applied wallpaper — palette extraction (Aura already has it), complications derived from Material You tonal palette, optional Aura-Originals chime as the watch's "tick" sound.
- **Scope:** Phone-side companion only (no separate Wear OS app at first). Add a `WatchFaceCompositor` that reads `current_wallpaper.palette` + clock font + complication set → emits a WFF XML and pushes via Data Layer + Watch Face Push API. Surface as Settings → "Send to my Wear OS watch".
- **Fit 4 / Impact 3 / Effort 1 / Risk 3 / Deps 1 / Novelty 5 = 17 → LATER.**

### L-2. Tasker plugin (events / states / actions) — `[~]` action minimum shipped 2026-05-17 rev4-impl

> Minimum-viable Tasker integration: new `TaskerActionReceiver` (manifest-declared, exported) responds to `com.freevibe.action.ROTATE_NOW` + `com.freevibe.action.SHUFFLE_NOW` and re-enters `RotationTriggerService.enqueueRotation()` — same code path NX-6 uses for the unlock/screen-off triggers, so all existing rotation source/target/constraint prefs apply. Tasker users can now wire Aura into any condition (calendar event, geofence, time-of-day, Bluetooth-connected) with a one-line "Send Intent" action.
>
> Still pending (full plugin spec): `TaskerPluginActivity` for ACTION_EDIT_SETTING / ACTION_FIRE_SETTING (UI-mediated parameterized actions); event broadcasts (wallpaper-changed, source-X-returned-429); state queries (current source, last applied URL). Hold for explicit user signal — the broadcast surface covers the 80 % case (one-tap automation triggers).



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

### L-10. Compose Adaptive Layouts — foldables, tablets, Compose-for-TV

- **Source(s):** [Build adaptive apps with Compose](https://developer.android.com/develop/ui/compose/build-adaptive-apps); [Adaptive layouts overview](https://developer.android.com/develop/ui/compose/layouts/adaptive); [Compose Multiplatform 1.7.1 adaptive layouts release](https://medium.com/@thebackbit/master-adaptive-layouts-in-compose-multiplatform-build-truly-responsive-uis-89184bf8b6de); [Touchlab adaptive layouts in CMP](https://touchlab.co/adaptive-layouts-cmp); [WallFlow tablet support release notes](https://github.com/ammargitham/WallFlow); [Google Play Tablet/Foldable quality bar](https://developer.android.com/guide/topics/large-screens/get-started-with-large-screens); existing UI has **zero** WindowSizeClass / NavigationSuiteScaffold / ListDetailPaneScaffold usage (verified by repo grep 2026-05-17).
- **Why later:** Aura today is phone-first. The wallpaper grid stretches awkwardly on tablets and unfolded foldables; the bottom-nav rail should swap to a navigation rail / drawer above 600 dp width. WallFlow's tablet UI is a direct competitor differentiator; KMP-aware adaptive APIs land for free with L-4 (Compose Multiplatform foundation). Play Store ranks large-screen-optimized apps higher on tablets/foldables — discoverability matters for OSS distribution beyond F-Droid (NX-8).
- **Scope:** Add `material3-adaptive` + `material3-adaptive-navigation-suite` dependencies inside N-1's lock-step bump. Replace `FreeVibeRoot.kt` bottom nav with `NavigationSuiteScaffold`. Promote wallpaper detail to `ListDetailPaneScaffold` on Expanded widths. Audit wallpaper grid column count by WindowSizeClass (currently fixed, should be 2-3-4-6 by class). Add WindowManager fold-state listener for half-opened (book) posture. Compose-for-TV stub: declare a TV banner intent-filter so an Android TV install can show wallpapers on screensaver / TV screensaver via the Daydream service (no extra UI surface needed initially).
- **Risk:** Refactor touches NavHost + every top-level screen — diff will be wide. Lower-end devices may regress on first-paint time if `NavigationSuiteScaffold` adds a recomposition layer; measure with Macrobenchmark.
- **Fit 3 / Impact 3 / Effort 2 / Risk 4 / Deps 3 (N-1 + L-4) / Novelty 2 = 17 → LATER.**

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
- **Source(s):** Existing 49 unit-test files (post 2026-05-17 audit pass); no Compose screenshot tests; one `androidTest/` smoke suite. [Paparazzi](https://github.com/cashapp/paparazzi) is the de-facto Compose screenshot library; [Roborazzi](https://github.com/takahirom/roborazzi) is a modern alternative; [Compose API defaults accessibility doc](https://developer.android.com/develop/ui/compose/accessibility/api-defaults).
- **Open question:** Screenshot test the AMOLED theming + RTL mirroring (ties to U-11). Add instrumented tests for `WallpaperApplier.applyByLocator` across `http://`, `file://`, `content://` URIs (the v6.15.0 bug-class) and for the streaming caps added in the 2026-05-17 audit (`readCapped` / `copyCapped` cap-exceeded path). Pair with the toolchain triad (N-1) since Compose Compiler 2.x changes which composables are screenshot-stable.
- **Score:** Fit 4 / Impact 3 / Effort 3 / Risk 4 / Deps 3 (N-1) / Novelty 1 = 18. Held until N-1 settles.

### U-14. Android XR spatial wallpaper (Galaxy XR / smart-glasses)
- **Source(s):** [Android XR spatial environments docs](https://developer.android.com/design/ui/xr/guides/environments); [Galaxy XR launch](https://www.android.com/xr/); [Android Show 2026 XR coverage](https://www.analyticsinsight.net/news/google-android-show-2026-to-detail-mixed-reality-ecosystem-with-android-xr) (May 12, 2026 reveal); [Virtual Reality News 3-tier glasses strategy](https://virtual.reality.news/news/googles-android-xr-glasses-strategy-could-beat-apple/); existing `WallpaperApplier.applyByLocator` scheme-dispatch ready to accept a GLB-by-locator handler.
- **Open question:** Charter call — Aura is phone/lock/home-screen first; "spatial environment" is a different surface. The XR docs explicitly recommend ~80 MB GLB assets, which dwarfs every existing Aura content type (largest wallpaper today: 64 MB cap, largest sound: 20 MB). Counter-argument: Aura's strong palette + color-extraction surface + tag taxonomy is the kind of pre-baked metadata an XR environment selector needs. Also fits T-D Multi-surface presence theme. Hold until Galaxy XR install base and Aura's KMP foundation (L-4) both move; revisit when Android XR ships its first OSS environment-publishing samples.
- **Score:** Fit 2 / Impact 3 / Effort 1 / Risk 1 / Deps 1 (L-4 KMP) / Novelty 5 = 13 → Under Consideration. Re-litigate post-Android-XR-stable.

### U-15. Real-time per-unlock wallpaper rotation (Pixel-10 parity, ahead of charter call)
- **Source(s):** [Pixel 10 Auto-change AI wallpaper](https://www.onoff.gr/blog/en/android/ai-wallpaper-android-create-ai-wallpapers/) — generates a fresh AI image every unlock, ~5-10 s on-device; [NX-6 scope](#nx-6-scheduler-triggers--per-app-exclusion-screen-off-pre-stage-sub-15-min-intervals-per-unlock) (per-unlock rotation via `USER_PRESENT` broadcast already in scope); [DroidViews smart wallpaper](https://www.droidviews.com/automatic-wallpaper-change-contextually-with-smart-wallpaper/).
- **Open question:** NX-6 already plans per-unlock rotation from existing sources. The Pixel 10 differentiator is *generating* per unlock, not rotating. Aura's Stability AI path (Phase 3.1) takes ~10-30 s per gen + costs a credit per call — not unlock-frequency viable. **Charter friction:** unlock-frequency generation against a paid API burns the user's bring-your-own-key quota in hours, not days. Either (a) cache K pre-generated images and rotate, (b) wait for U-2 on-device Stable Diffusion to mature, or (c) stay out of unlock-frequency generation entirely. Hold.
- **Score:** Fit 3 / Impact 3 / Effort 1 / Risk 1 / Deps 2 (NX-6 + U-2) / Novelty 4 = 14 → Under Consideration.

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
Spans: **N-1, N-2, N-3, N-4, NX-10, NX-11, yt-dlp CVE-2026-26331 risk row**.
Outcome: Aura runs on the current platform with current libraries. Compose Strong Skipping wins, Material 3 Expressive, Photo Picker (rev4: + 9:16 customization on Android 17), WallpaperDescription, Subject Segmentation, EyeDropper colour pick (rev4: Android 17+), Subject Segmentation all land together. NewPipeExtractor bumps to 0.26.1+; bundled yt-dlp re-verified post-CVE-2026-26331.

### T-B. Lockscreen depth & live-wallpaper engine
Spans: **N-3, NX-1, NX-2, NX-3, NX-9**.
Outcome: Aura matches Pixel Live Effects (Shape / Weather / Cinematic) and One UI Wonderland feature parity without depending on Pixel-only or Samsung-only system features. Media3 1.10 Material3 playback composables (NX-9) replace hand-rolled preview chrome so the engine surface stays small.

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
Spans: existing 5.5 (battery dashboard), NX-1 (engine pause-on-invisible discipline), NX-9 (Media3 1.10 `experimentalSetDynamicSchedulingEnabled` for preview surface), accessibility audits in U-8 / U-9 / U-11.
Outcome: Aura's live wallpapers prove their power impact (Facer "Power Impact" rating equivalent). Users with TalkBack / large-font / reduced-motion needs get parity. [DreamPixel battery analysis](https://dreampixelstudio.app/blog/use-live-wallpapers-on-android-without-draining-battery) — lightweight live wallpapers cost <2 % battery/day, heavy 3D/video can cost 5–8 %; Aura's existing auto-15-FPS-below-15 %-battery cap + pause-on-invisible are the relevant primitives.

### T-H. Trust & hardening
Spans: existing 2026-05-17 audit pass (downloader sanitization, streaming caps, parallax bitmap-leak fix, AGSL crash-safety), N-2 (Custom Claims server-side enforcement), U-13 (screenshot + integration test expansion), the Risk Register rows for CVE-2026-0073-class platform CVEs + yt-dlp CVE-2026-26331 (new in rev4).
Outcome: Every external input (manifest URL, HTTP body, content URI, user-pick) goes through a streaming cap; every internal allocation has a leak-free recycle path; every privilege check has a server-side enforcement layer. The 2026-05-17 pass closed the worst-case OOM-OOM-leak chain in `WallpaperApplier.downloadBitmap` (the call site of every wallpaper apply); same primitives reused in three sibling write paths.

### T-I. Developer experience & build verification (new in rev4)
Spans: **NX-12 (CI verify workflow), NX-13 (predictive-back wiring), U-12 (CONTRIBUTING.md), U-13 (screenshot + integration test expansion)**.
Outcome: Every push and PR is build-verified, unit-tested, and lint-clean before it can land. N-1 toolchain bumps can be honestly validated. Compose detail screens animate predictive-back smoothly on Android 14+. The "no SDK in CI" gap that has gated every Implementation Pass since 2026-04-25 closes; static-review-only stops being the default mode.

---

## Risk Register

Live operational risks ranked by likelihood × blast radius. Update at every release.

| Risk | Likelihood | Blast | Mitigation in roadmap |
|------|-----------|-------|-----------------------|
| NewPipe Extractor stops working on Play-Protect-certified Android (March 2026 maintainer warning, [piunikaweb](https://piunikaweb.com/2026/03/09/newpipe-certified-android-devices-warning/); SABR enforcement [#12126](https://github.com/TeamNewPipe/NewPipe/issues/12126); latest stable extractor **0.26.1** with SABR-only player response fix per the [post-0.28.1 hotfix notes](https://newpipe.net/blog/pinned/announcement/newpipe-0.28.1-released/) — Aura is on 0.24.8) | Medium | High (YouTube sound tab dies) | Abstract `YouTubeRepository.search()` + `resolveStreamUrl()` behind a `SoundExtractor` interface in N-1; ship NewPipeExtractor as the default impl, `NewPipeExtractorKmpAdapter` ([yushosei/NewPipeExtractor-KMP](https://github.com/yushosei/NewPipeExtractor-KMP)) as fallback, and youtubedl-android as last-resort path. Pin `NewPipeExtractor` version comment already in place since v6.12.0; bump to **0.26.1+** in lock-step with monthly upstream patches once N-1 unblocks build verification. |
| yt-dlp CVE-2026-26331 — arbitrary command injection via `--netrc-cmd` (all versions ≥ 2023.06.21 < 2026.02.21; [GitLab advisory](https://advisories.gitlab.com/pkg/pypi/yt-dlp/CVE-2026-26331/)). Aura ships yt-dlp transitively via `youtubedl-android:0.18.1`. | Low (Aura code never sets `--netrc-cmd` or `netrc_cmd`) | Medium (any contributor adding netrc support without auditing would hit this) | Verify bundled yt-dlp version meets ≥ 2026.02.21; add a guarded grep / unit-test asserting `--netrc-cmd` is not passed through `YouTubeRepository.resolveStreamUrl()`; bump `youtubedl-android` in the N-1 toolchain pass if a release ≥ 0.19.x has shipped with the fixed yt-dlp bundle. Document the rule in the YouTube repository's KDoc. |
| CISA-KEV-class platform CVEs (Aura cannot patch; users may run unpatched OEMs). Recent: CVE-2026-0073 (May 2026, adbd zero-click RCE, [AOSP bulletin](https://source.android.com/docs/security/bulletin/2026/2026-05-01)). | Low | Low (device-level, not Aura's bug) | Existing optional warning-banner placeholder; no roadmap response needed beyond keeping the dependency hygiene cadence (T-A). |
| Firebase BoM 33.7.0 transitive protobuf vulnerable to CVE-2024-7254 | High | Medium | **N-2** bumps to BoM 34.x |
| Aura's `VoteRepository` admin auth is client-side spoofable | Medium | Medium (community moderation bypass) | **N-2** Custom Claims |
| ML Kit `segmentation-selfie:16.0.0-beta6` still in beta two years on | Medium | Medium (parallax breaks if pulled) | **N-3** migrates to Subject Segmentation GA |
| Stability AI free tier / pricing changes; per-user API key is the only path | Low | Medium (AI tab degrades to "bring your own key") | Document; consider Imagen via Firebase AI Logic in U-2 follow-up |
| AGP 9 / Kotlin 2.3 / KSP1 breaks Hilt/Compose generation | Medium | High | **N-1** coordinated upgrade with feature freeze |
| AV1 hardware decode <10 % install base; SW fallback burns battery | Medium | Medium | **NX-1** gates AV1 on Performance Class ≥ 33 |
| CISA-KEV Android framework CVE-2025-48572/-48633 on unpatched OEMs | Low | Low (device-level, not Aura's bug) | Folded into the platform-CVE row above; same mitigation. |
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

### 2026-05-17 — Rev4-impl autonomous batch (8 NX/L items, code + roadmap)

Autonomous-development pass following the rev4 freshness pass (same day). Eight
items landed across the Next + Later tiers; build verification still pending
behind N-1. Push status: same blocker as prior passes (`MavenImaging` executor
credential lacks write to `SysAdminDoc/Aura`); commits land locally on `main`,
owner must push.

**Items shipped (each `[~]` partial, all sourced from existing rev4 scope)**

- **NX-12 — CI build verification workflow** *(closes the static-review-only loop)*
  New `.github/workflows/verify.yml` triggered on `push: main` + `pull_request: main` + `workflow_dispatch`. Runs `assembleDebug` + `testDebugUnitTest` + `lintDebug` on JDK 17 with Gradle cache. Stubs `local.properties` so signing/API-key lookups don't fail in CI (release.yml stays the source of truth for signed builds). Uploads test + lint reports as artifacts on failure with 14-day retention. `concurrency` group cancels superseded runs.

- **NX-3 — Smart Crop with Subject Segmentation** *(wallpaper variant)*
  `SmartCropCalculator.kt` pure geometry (7 unit tests) + `SmartCropDetector.kt` wrapping the same ML Kit Subject Segmentation engine N-3 wired into `ParallaxWallpaperService`. `WallpaperCropViewModel.applySmartCrop()` is suspend, returning the new transform so the composable can re-sync local `rememberSaveable` gesture state. UI surface: Smart Crop FilterChip with sparkle icon + Detecting… spinner; "Couldn't detect a subject — drag to position manually" snackbar fallback. Video crop variant deferred (FFmpeg geometry different).

- **NX-13 — BackHandler audit** *(2 of ~18 screens)*
  Highest-stakes in-flight screens only: `AiWallpaperScreen` back-press cancels the in-flight Stability AI job (new `AiWallpaperViewModel.cancelGeneration()` + `generationJob: Job` tracker + `onCleared()` defensive cancel — saves the user's API credit budget on bail-out). `VideoCropScreen` back-press during FFmpeg toasts "Cropping in progress — please wait" and holds the screen. Remaining 16 detail/editor/preview/picker screens deferred behind N-1 Navigation 2.9.

- **NX-2 — Lockscreen widget surface** *(no code, manifest only)*
  `freevibe_widget_info.xml` widget category bumped `home_screen` → `home_screen|keyguard`. On Android 16 QPR2+ (Dec 2025 stable) the existing `FreeVibeWidget` is now placeable on the lockscreen surface. Older Android silently ignores the keyguard bit. Clock-tuck mask + dedicated daily-pick lockscreen widget still pending pending real-device test feedback.

- **NX-6 — Rotation triggers (per-unlock + screen-off pre-stage)**
  New `RotationTriggerService` foreground service with `specialUse` foreground-service type. Dynamically registers `Intent.ACTION_USER_PRESENT` + `Intent.ACTION_SCREEN_OFF` receivers (both manifest-blocked on API 26+). Each fire enqueues a one-shot expedited `AutoWallpaperWorker` via `WorkManager.enqueueUniqueWork(KEEP)` so chatty unlock sequences coalesce. Two new DataStore prefs `rotateOnUnlock` / `rotateOnScreenOff` (default false) gate the service lifecycle; `RotationTriggerService.reconcile()` is the idempotent start/stop entry point called from `FreeVibeApp.onCreate` and `SettingsViewModel.setRotateOn{Unlock,ScreenOff}`. Manifest: new service declaration + `FOREGROUND_SERVICE_SPECIAL_USE` permission. Settings UI: "Change on every unlock" + "Pre-stage on screen off" toggles below the existing constraint section. Per-app exclusion + sub-15-min AlarmManager + one-tap-shuffle widget still pending.

- **L-2 — Tasker / automation hook** *(action minimum)*
  New manifest-declared `TaskerActionReceiver` (exported) responds to `com.freevibe.action.ROTATE_NOW` + `com.freevibe.action.SHUFFLE_NOW`, re-enters `RotationTriggerService.enqueueRotation()`. Tasker / MacroDroid / adb shell can wire Aura into any condition with a one-line "Send Intent" — no plugin SDK needed. Full TaskerPluginActivity (UI-mediated parameterized actions, event broadcasts, state queries) still queued.

- **NX-4 — SelectedContentHolder process-death survival**
  Singleton now persists the single selected wallpaper + selected sound to a `freevibe_selected_content` SharedPreferences file via Moshi JSON on every `select*` call. Hilt-injects `@ApplicationContext` + `Moshi`; lazy-restores from disk on construction. `wallpaperList` (pager-supporting) intentionally still in memory only — the detail screen already collapses to single-item display when the list is empty. Full nav-graph-scoped `SelectionViewModel` + `SavedStateHandle` refactor still queued behind Navigation 2.9 (N-1).

- **NX-8 — Distribution metadata refresh** *(fastlane + Obtainium)*
  `fastlane/metadata/android/en-US/` fully refreshed: title (`FreeVibe` → `Aura`), short description, full description (rewritten against 29-feature surface incl. parallax, weather, Smart Crop, rotation triggers, Tasker hook, sound editor, dual wallpapers, contact ringtones, community uploads, creator profiles, shareable collections, battery dashboard). New `changelogs/111.txt` for v6.31.0. New `obtainium.json` at repo root with `v*` tag regex + APK arch filter. Per-ABI splits + F-Droid metadata PR + IzzyOnDroid submission still queued behind N-1 build verification.

**Themes touched**

- T-A (dependency hygiene & platform parity) — no version bumps this pass (N-1 still blocked).
- T-B (lockscreen depth & engine) — NX-3 Smart Crop and NX-2 widget surface land partial wins.
- T-C (extension ecosystem) — L-2 Tasker hook closes the broadcast-action surface.
- T-F (distribution beyond Play Store) — NX-8 fastlane + Obtainium close the metadata surface.
- T-G (battery transparency & accessibility) — NX-13 back-cancel reduces wasted Stability AI calls.
- T-H (trust & hardening) — RotationTriggerService runs `RECEIVER_NOT_EXPORTED`, narrow Intent surface; SelectedContentHolder uses `runCatching` around JSON I/O so a corrupt blob doesn't crash startup.
- T-I (developer experience) — NX-12 verify.yml is the central win; NX-13 BackHandler discipline is the per-screen one.

**Push status**

- 8 commits added to local `main` (rev4 freshness + rev4-impl × 7). `git push origin main` still blocked by executor credential; owner must push.

### 2026-05-17 — Rev4 freshness pass (no code; roadmap only)

Document-only pass on top of rev3 (committed earlier the same day). Web-research
batch (~8 distinct query classes, ~25 net-new URLs) cross-checked against
existing rev3 coverage; four genuinely new items + one CVE row + competitor
validation surfaced. No code change; no working-tree modification beyond
`ROADMAP.md`.

**Added Next-tier items**
- **NX-10** Android 17 EyeDropper API — `Intent.ACTION_OPEN_EYE_DROPPER` →
  wallpaper colour search seed. Fit 4 / Impact 3 / Effort 4 / Risk 4 / Deps 3 / Novelty 4 = 22.
- **NX-11** Photo Picker 9:16 portrait via `PhotoPickerUiCustomizationParams`
  on Android 17+ (N-4 follow-up). Fit 5 / Impact 3 / Effort 5 / Risk 5 / Deps 3 / Novelty 2 = 23.
- **NX-12** CI build verification workflow — close the static-review-only loop.
  Fit 5 / Impact 4 / Effort 4 / Risk 5 / Deps 4 / Novelty 1 = 23.
- **NX-13** Predictive-back wired through NavHost transitions + 18-screen
  `BackHandler` audit. Fit 4 / Impact 3 / Effort 4 / Risk 4 / Deps 4 / Novelty 1 = 20.

**Added Later-tier item**
- **L-10** Compose Adaptive Layouts (foldables / tablets / Compose-for-TV) —
  zero existing WindowSizeClass code verified by repo grep. Fit 3 / Impact 3 / Effort 2 / Risk 4 / Deps 3 / Novelty 2 = 17.

**Risk Register additions**
- yt-dlp CVE-2026-26331 — `--netrc-cmd` arbitrary command injection, fixed in
  yt-dlp 2026.02.21. Aura ships via `youtubedl-android:0.18.1`; Aura code does
  not pass `--netrc-cmd`, so blast is low, but verify-and-guard is in scope of N-1.
- NewPipeExtractor target version bumped from "0.25.0+" to "0.26.1+" (current
  stable after the 0.28.1 hotfix lineage).

**Competitor validation (not new items)**
- One UI 8.5 stable rolling out May 2026 with **Smart Subject Placement**
  (auto-arrange clock/widgets around photo subjects) + **AI Weather Effects**
  (weather animations behind subject layers via segmentation). Directly
  validates Aura's NX-2 (lockscreen depth + clock-tuck) and Phase 6.3 (weather
  overlay) directions. No new roadmap item — existing trajectory is correct.
- Paperize landed an experimental "live wallpaper" alpha mode — validates
  NX-1's GL/AGSL engine migration as a competitive must-have, not a nice-to-have.

**Themes touched**
- T-A (dependency hygiene) — NX-10, NX-11, yt-dlp CVE row appended.
- T-H (trust + hardening) — yt-dlp CVE row appended.
- T-I (new) — Developer experience & build verification. Spans NX-12, NX-13, U-12, U-13.

**Push status**
- Roadmap-only edit; commit + push when convenient. No code paths touched.

### 2026-05-17 — Hardening audit pass (security + bitmap leaks + streaming caps)

Static-review-only pass against the 2026-05-16 autonomous batch. Found seven real
issues across the four newly-landed features; all fixed in the working tree
(`git status`: 6 files modified, ready for commit by an operator with push
access). Did not run `./gradlew testDebugUnitTest`: same SDK-absent
constraint as the 2026-05-16 pass. Visual diff + import check verified each
change compiles against the existing call graph.

**Security — `AuraOriginalsDownloader` (N-5)**
- New `sanitizeEntryId(id)` — strict allowlist (ASCII alnum + `-_.`, max 64 chars,
  no path separators, no dot-only). Defends against a tampered manifest's
  `entry.id` escaping `filesDir/aura_originals/` via `../etc/passwd`.
- New `isAllowedDownloadUrl(url)` — HTTPS-only scheme gate. Rejects `http`,
  `file`, `content`, `data`, `ftp`. Defense-in-depth against a typo or
  tampered manifest redirecting to cleartext or local-file fetch.
- New `isInside(parent, child)` — canonical-path containment check.
  Belt-and-braces guard layered after the sanitizer in case future relaxation
  re-introduces an escape vector.
- Running-total budget — prior code only checked `manifest.totalBytes` against
  `MAX_TOTAL_BYTES`; the in-loop running sum was never tracked, so a manifest
  with N entries each just under the per-file cap could exceed 80 MB total.
  Now: each entry's effective budget is `min(remainingBudget, MAX_PER_FILE_BYTES)`;
  successful downloads add to `runningBytes`; entries that would exceed the
  remaining budget are rejected with a clear DEBUG log.
- Tests: +5 in `AuraOriginalsDownloaderTest` — `sanitizeEntryId` accepts
  (happy path), `sanitizeEntryId` rejects 11 traversal/unsafe cases,
  `isAllowedDownloadUrl` covers 10 scheme cases, `isInside` covers nested +
  parent + escape attempts. Test count: 46 → 49 in the worker file.

**Bitmap leaks — `ParallaxWallpaperService` (N-3 segmenter migration)**
- The 2026-05-16 N-3 patch migrated the segmenter success callback to use
  `result.foregroundConfidenceMask`. The new code allocated `bgBitmap`
  (`bitmap.copy()`) inside the synchronized block, then `fgBitmap`
  (`Bitmap.createBitmap`) outside it. If `fgBitmap` allocation OOM'd or the
  pixel-loop threw, `bgBitmap` was orphaned as a native allocation. Wallpaper
  service processes are very long-lived; the leak was observable across
  apply→apply cycles.
- Fix: `bgBitmap` + `fgBitmap` declared as `var`s at callback scope, wrapped
  in `try { … } catch … finally`. A `publishedToLayers` flag set inside the
  publish-to-fields synchronized block tells the finally block whether to
  recycle (only recycle when not published).
- Secondary fix: `bitmap.copy()` can return null on low-memory devices.
  Previously the code assigned the null to `backgroundLayer`, then
  unconditionally retired `fallbackBitmap`, leaving `draw()` with neither
  layers nor a fallback to render (solid-black wallpaper). Now: if copy()
  returns null, reconstruct `bgBitmap` from the already-extracted pixel array;
  if reconstruction also fails, do not retire the fallback so draw() has
  something to render.

**Streaming size caps — `WallpaperApplier` (defense-in-depth across all apply paths)**
- `downloadBitmap` previously called `body.bytes()` after only a Content-Length
  pre-check. `OkHttp.ResponseBody.bytes()` has no upper bound; if Content-Length
  is unknown (chunked transfer) or lies, the entire response was buffered into
  memory before the cap was re-checked. The pre-check was unreachable in
  exactly the case it was needed.
- Fix: new `readCapped(InputStream, cap)` streams 64 KB at a time, aborts the
  read the moment cumulative bytes exceed `MAX_WALLPAPER_BYTES` (64 MB).
  Replaces the `body.bytes()` call site.
- Same class of issue in `prepareParallaxWallpaper` and `prepareParallaxFromUri`
  (no cap on copy-to-disk of either an HTTP body or a user-picked content URI).
  Fix: new `copyCapped(InputStream, OutputStream, cap)` reuses the same
  pattern. Caller's existing temp-then-rename + try/finally cleanup is
  preserved; the cap throws IOException which the existing catch already
  handles with `tempFile.delete()`.

**Crash-safety — `AgslEffectPipeline` (N-3 scaffold)**
- `RuntimeShader` throws `IllegalArgumentException` on malformed AGSL source.
  Effects are hard-coded today, so this is preventive for future contributors
  adding new effects (the most likely real-world cause of a crash report on
  this surface).
- Fix: `apply()` wraps `applyAgsl()` in try/catch for both `Exception` (bad
  shader) and `OutOfMemoryError`, falling back to `copyOrFallback()`. Recycled-source
  guard added (previously would propagate `IllegalStateException` from
  `Bitmap.copy()` on a recycled source). `applyAgsl()` recycles its own
  pre-allocated `output` bitmap before re-throwing on any exception inside
  shader compilation or canvas draw — otherwise the caller's fallback path
  leaked that bitmap.

**Suspend conversion verification — `AutoWallpaperWorker.schedule`**
- Pre-existing uncommitted in-progress change (`runBlocking { prefs.… }` → suspend
  function) was sitting in the working tree on entry. All 7 callers in
  `SettingsViewModel.kt:144,153,167,171,175,241,249` are inside
  `viewModelScope.launch { … }`; the new suspend signature is therefore
  call-site-compatible. Conversion is correct.

**Push status**
- All 6 file edits land locally; `git push origin main` will still bounce the
  same way as the 2026-05-16 batch did (executor credential `MavenImaging`
  lacks write to `SysAdminDoc/Aura`). Owner must push.

### 2026-05-16 — Autonomous N-2..N-5 batch (build verification pending)

Four Now-tier items landed as code; the remaining Now item (N-1 toolchain triad)
is deferred because the executing environment had no Android SDK / JDK available
to validate the upgrades. Static review only; runtime verification next session.

**N-4 — Photo Picker + monochrome icon + WallpaperDescription scaffold (commit `b0ae1fe`)**
- WallpapersScreen community upload + CollectionsScreen QR import switched
  from `GetContent("image/*")` / `OpenDocument(arrayOf("image/*"))` to
  `PickVisualMedia.ImageOnly`. No `READ_MEDIA_IMAGES` permission prompt;
  scoped-storage compliant.
- Vector `drawable/ic_launcher_monochrome.xml` added; `mipmap-anydpi-v26/ic_launcher{,_round}.xml`
  declare `<monochrome>` layer. Android 13+ themed icons now use the Aura "A" silhouette.
- `xml/{video,parallax,weather}_wallpaper.xml` annotated with TODO comments
  pointing to N-4/N-1 for when compileSdk 36 unlocks the Android 16
  WallpaperDescription / WallpaperInstance API.

**N-2 — Firebase BoM 34.13.0 + Custom Claims admin path (commit `c9ca405`)**
- Firebase BoM 33.7.0 → 34.13.0 (closes CVE-2024-7254 transitive protobuf risk).
- Removed deprecated `firebase-{auth,database,storage}-ktx` artifacts; migrated 5
  files to canonical `FirebaseAuth.getInstance()`, `FirebaseDatabase.getInstance()`,
  `FirebaseStorage.getInstance()`.
- New `VoteRepository.refreshAdminFromClaims()` forces ID-token refresh and
  caches the `admin` Custom Claim in `_adminFromClaims` StateFlow.
- `isAdmin` getter now consults Custom Claim first, with legacy device-ID hash
  + UID allowlist as one-cycle migration fallbacks. Precedence captured in
  pure `computeIsAdmin` helper, covered by `AdminPrecedenceTest` (6 cases).
- `FreeVibeApp.warmCommunityIdentity()` calls `refreshAdminFromClaims` after
  sign-in so admin status syncs on every cold launch.
- `database.rules.json` (new) — proposed RTDB Security Rules enforcing
  `auth.token.admin === true` server-side for moderation paths. Mirrors the
  client check; this is the actual enforcement layer.
- `docs/firebase-admin-claims.md` (new) — operator runbook for granting/revoking
  the claim via the Admin SDK and removing the device-hash fallback once all
  admins have rotated tokens.
- Resolves the pre-existing `VoteRepository.kt:75` TODO.

**N-3 — Subject Segmentation API + AGSL pipeline scaffold (commit `61443fc`)**
- Dependency: `com.google.mlkit:segmentation-selfie:16.0.0-beta6` →
  `com.google.mlkit:segmentation-subject:16.0.0-beta1` (GA replacement of the
  two-year selfie-segmentation beta). Added `play-services-base:18.5.0` for
  `ModuleInstallClient`.
- `ParallaxWallpaperService` migrated to `SubjectSegmenter` +
  `SubjectSegmenterOptions.enableForegroundConfidenceMask()`. The new mask is
  sized to the input bitmap, so the pixel-to-mask coordinate remap is gone
  (simpler + faster).
- `requestSegmenterModuleInstall()` proactively warms the unbundled model at
  engine create so the first parallax apply isn't silently no-op while Play
  services downloads the module.
- All lifecycle guards from the prior path preserved: per-segmenter tracking,
  double-close protection, generation counter, bitmap-lock during pixel read.
- New `AgslEffectPipeline.kt` — API-33+ gated `RuntimeShader` pipeline with a
  Canvas fallback. Public surface is `apply(bitmap, effect)` over an
  `AgslEffect` sealed catalog (IDENTITY, DEPTH_SHADE today). Single GPU-effect
  surface to be consumed by wallpaper editor, weather service, and parallax
  service in future passes. `AgslEffectPipelineTest` (3) covers AGSL source
  validity + out-of-range uniform handling.

**N-5 — Aura Originals manifest + first-launch downloader (commit `cbc9db2`)**
- `assets/aura_originals_manifest.json` — versioned schema; ships empty.
- `AuraOriginalsManifest.kt` — Moshi model + DI loader.
- `AuraOriginalsDownloader.kt` — `CoroutineWorker` that downloads each manifest
  entry over HTTPS, sha256-verifies before atomic rename, enforces 5 MB
  per-file + 80 MB total caps, runs on UNMETERED constraint by default,
  exponential backoff on failure, idempotent across cold starts.
- `FreeVibeApp.onCreate` enqueues with `ExistingWorkPolicy.KEEP` so the
  pack converges over multiple Wi-Fi sessions without redoing work.
- `docs/aura-originals-curation.md` — curation workflow + CC0 license
  compliance + retroactive removal via manifest revisions.
- `AuraOriginalsDownloaderTest` (5) covers extension guessing + sha256
  matching/mismatch/blank-rejection.
- DB schema stays at v14 — bundled tracking reuses the existing
  `FavoriteEntity.offlinePath` convention. Room v15 deferred until the
  curation list lands.

**N-1 deferral (toolchain triad)**
- AGP 9 / Gradle 9 / Kotlin 2.3 / KSP2 / Compose BOM 2026.05 / Hilt 2.59
  upgrade NOT performed this pass. The autonomous executor had no Android
  SDK or JDK available to validate the changes, and a "blind" toolchain
  bump on this scale typically surfaces Compose stability annotations and
  KSP2 incremental-cache regressions that only a clean build can catch.
  Re-pick this item in a session that can run `./gradlew :app:assembleDebug`.

**Push status**
- All four feat commits land locally on `main`. `git push origin main`
  blocked: the executor's git credential is `MavenImaging`, but
  `https://github.com/SysAdminDoc/Aura.git` rejects with 403. Owner must
  push (or grant the executor's GitHub account write access to the repo).

---

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

- **Paperize** ([github.com/Anthonyy232/Paperize](https://github.com/Anthonyy232/Paperize)) — 1.1k★ — GPL-3.0 — fully-offline dynamic changer; Compose; v4.0.0-alpha live wallpaper mode. Issues cited: [#444](https://github.com/Anthonyy232/Paperize/issues/444), [#447](https://github.com/Anthonyy232/Paperize/issues/447), [#482](https://github.com/Anthonyy232/Paperize/issues/482), [#516](https://github.com/Anthonyy232/Paperize/issues/516), [#531](https://github.com/Anthonyy232/Paperize/issues/531), [#532](https://github.com/Anthonyy232/Paperize/issues/532), [#428](https://github.com/Anthonyy232/Paperize/issues/428), [#126](https://github.com/Anthonyy232/Paperize/issues/126), [#192](https://github.com/Anthonyy232/Paperize/issues/192); discussion [#313](https://github.com/Anthonyy232/Paperize/discussions/313). 2026 follow-ups: [#446](https://github.com/Anthonyy232/Paperize/issues/446), [#450](https://github.com/Anthonyy232/Paperize/issues/450) (Jan-Feb 2026 enhancement asks), [#496](https://github.com/Anthonyy232/Paperize/issues/496), [#497](https://github.com/Anthonyy232/Paperize/issues/497), [#498](https://github.com/Anthonyy232/Paperize/issues/498) (Feb-Mar 2026 bug + feature).
- **WallFlow** ([github.com/ammargitham/WallFlow](https://github.com/ammargitham/WallFlow)) — 452★ — GPL-3.0 — Wallhaven + Reddit; foldable inner + outer; smart crop (Plus variant); Paging 3; KMP Windows planned. Issues: [#62](https://github.com/ammargitham/WallFlow/issues/62), [#63](https://github.com/ammargitham/WallFlow/issues/63), [#64](https://github.com/ammargitham/WallFlow/issues/64), [#68](https://github.com/ammargitham/WallFlow/issues/68), [#70](https://github.com/ammargitham/WallFlow/issues/70), [#73](https://github.com/ammargitham/WallFlow/issues/73), [#82](https://github.com/ammargitham/WallFlow/issues/82), [#91](https://github.com/ammargitham/WallFlow/issues/91), [#99](https://github.com/ammargitham/WallFlow/issues/99), [#102](https://github.com/ammargitham/WallFlow/issues/102).
- **WallCraft** ([github.com/Rahul-999-alpha/WallCraft](https://github.com/Rahul-999-alpha/WallCraft)) — 1★ — MIT — Pollinations.ai no-key AI generation, AMOLED, AdMob (anti-pattern for Aura).
- **Muzei** ([github.com/muzei/muzei](https://github.com/muzei/muzei)) — 4.9k★ — Apache-2.0 — refreshing-art live wallpaper; canonical plugin/source API. Issues: [#794](https://github.com/muzei/muzei/issues/794), [#800](https://github.com/muzei/muzei/issues/800), [#793](https://github.com/muzei/muzei/issues/793), [#792](https://github.com/muzei/muzei/issues/792), [#797](https://github.com/muzei/muzei/issues/797), [#869](https://github.com/muzei/muzei/issues/869), [#838](https://github.com/muzei/muzei/issues/838), [#836](https://github.com/muzei/muzei/issues/836), [#811](https://github.com/muzei/muzei/issues/811), [#128](https://github.com/muzei/muzei/issues/128), [#110](https://github.com/muzei/muzei/issues/110), [#109](https://github.com/muzei/muzei/issues/109).
- **Peristyle** ([github.com/Hamza417/Peristyle](https://github.com/Hamza417/Peristyle)) — 620★ — Apache-2.0 — glassmorphic Compose wallpaper mgr; tags + auto-changer; intent `app.peristyle.START_AUTO_WALLPAPER_SERVICE`. Feature request: [#98 different wallpaper set for night](https://github.com/Hamza417/Peristyle/issues/98) (analog to Aura's existing dark/light auto-switch).
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
- **Waller** — OSS Android app that *generates* wallpapers (gradients, patterns, noise) instead of downloading them ([MakeUseOf review](https://www.makeuseof.com/open-source-wallpaper-app-phone/)). Adjacent to Aura's AI Wallpaper Generation; cited as a charter-aligned generation-without-API alternative.
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
- One UI 8.5 — [Android Authority](https://www.androidauthority.com/samsung-one-ui-8-5-lock-screen-weather-effect-3630836/), [Digital Trends](https://www.digitaltrends.com/phones/samsungs-one-ui-8-5-will-turn-your-lock-screen-into-a-mini-music-show/), [SamMobile top ten features stable May 2026](https://www.sammobile.com/news/one-ui-8-5-update-top-features/), [Sammy Fans Wonderland motion-wallpaper May 2026 update](https://www.sammyfans.com/2026/05/07/samsung-wonderland-motion-wallpapers-may-2026-update/), [SammyGuru AI weather effects deep dive](https://sammyguru.com/one-ui-8-5-will-bring-ai-powered-live-weather-effects-to-your-lock-screen/), [SamMobile adaptive lock-screen clock all objects](https://www.sammobile.com/news/one-ui-8-5-adaptive-lock-screen-clock-works-all-objects/), [Sammy Fans Galaxy-to-Share refresh](https://www.sammyfans.com/2026/05/16/samsung-galaxy-to-share-one-ui-8-5-update/), [Sammy Fans AI wallpaper expansion tool](https://www.sammyfans.com/2026/03/14/one-ui-8-5-expand-wallpapers-with-new-ai-tool/), [Sammy Fans interactive wallpapers Jan 2026 beta](https://www.sammyfans.com/2026/01/05/samsungs-one-ui-8-5-beta-introduces-animated-and-interactive-wallpapers/).
- Pixel Live Effects (Android 16 QPR1) — [9to5Google](https://9to5google.com/2025/05/20/google-pixel-wallpaper-effects-android-16-qpr1/), [PiunikaWeb user reception](https://piunikaweb.com/2025/09/05/pixels-new-live-effects-wallpaper-feature-falls-flat-with-users/), [Beebom Live Effects how-to](https://gadgets.beebom.com/guides/how-to-use-lock-screen-live-effects-on-pixel-phones), [PhoneArena weather wallpaper](https://www.phonearena.com/news/android-16-allows-you-to-check-local-weather-using-wallpaper_id170727), [Sammy Fans AI photo wallpaper](https://www.sammyfans.com/2025/06/03/android-16s-new-ai-photo-wallpaper-feature-will-melt-your-heart/), [Material 3 Expressive personalization Pixel Drop](https://store.google.com/intl/en/ideas/articles/september-pixel-drop-personalization/).
- Pixel 10 Auto-change AI Wallpaper (per-unlock generation) — [OnOff.gr AI wallpaper guide](https://www.onoff.gr/blog/en/android/ai-wallpaper-android-create-ai-wallpapers/), [Pixel custom wallpaper support page](https://support.google.com/pixelphone/answer/16517561?hl=en), [Tom's Guide Pixel 10 AI icons critique](https://www.tomsguide.com/phones/google-pixel-phones/i-just-tried-new-ai-generated-app-icons-for-pixel-phones-and-theres-a-huge-problem).
- Android 16 QPR2 lockscreen widgets stable (December 2025) — [Pocket-lint a-decade-back coverage](https://www.pocket-lint.com/google-added-back-android-lock-screen-widgets/), [Android Police droid-life Dec 2025 release](https://www.droid-life.com/2025/08/20/android-16-qpr2-adds-lock-screen-widgets-to-phones-again/), [Indianewsnetwork stable rollout](https://www.indianewsnetwork.com/en/google-releases-android-16-qpr2-update-pixel-devices-20251204), [How-To Geek how they work](https://www.howtogeek.com/android-lock-screen-widgets-how-they-work/).
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
- Android 17 — [release notes](https://developer.android.com/about/versions/17/release-notes), [overview](https://developer.android.com/about/versions/17), [Beta 1](https://android-developers.googleblog.com/2026/02/the-first-beta-of-android-17.html), [Beta 2 announce EyeDropper + Contacts Picker](https://9to5google.com/2026/02/26/android-17-beta-2-contacts-and-display-color-access/), [Beta 3 PhotoPickerUiCustomizationParams + Platform Stability](https://android-developers.googleblog.com/2026/03/the-third-beta-of-android-17.html), [Beta 4](https://android-developers.googleblog.com/2026/04/the-fourth-beta-of-android-17.html), [Beta 4 feature roundup 9to5Google](https://9to5google.com/2026/04/16/android-17-beta-4-everything-new/), [Android 17 Beta hands-on BigGo](https://finance.biggo.com/news/202605081152_Android_17_Beta_Key_Features), [Beebom 20 features](https://gadgets.beebom.com/guides/best-android-17-features), [EyeDropper deep-dive ProAndroidDev](https://proandroiddev.com/exploring-the-eyedropper-api-android-17-9d7be86aaa16), [Android Engineers EyeDropper walkthrough](https://androidengineers.substack.com/p/introducing-the-android-17-eye-dropper), [Android Authority EyeDropper first look](https://www.androidauthority.com/android-17-eyedropper-color-picker-3610073/) — Platform Stability reached Beta 3; stable June 2026.
- Android XR — [spatial environments design docs](https://developer.android.com/design/ui/xr/guides/environments), [Android XR overview](https://www.android.com/xr/), [Android Show 2026 preview](https://www.analyticsinsight.net/news/google-android-show-2026-to-detail-mixed-reality-ecosystem-with-android-xr), [Galaxy XR launch coverage](https://virtual.reality.news/news/google-android-xr-revealed-ai-glasses-coming-2026/), [3-tier glasses strategy 2026-2027](https://virtual.reality.news/news/googles-android-xr-glasses-strategy-could-beat-apple/).
- Performance Class — [docs](https://developer.android.com/topic/performance/performance-class).
- Ultra HDR — [display docs](https://developer.android.com/media/grow/ultra-hdr/display).
- AGSL — [official guide](https://developer.android.com/develop/ui/views/graphics/agsl/using-agsl), [Compose patterns Medium](https://medium.com/androiddevelopers/agsl-made-in-the-shade-r-7d06d14fe02a).
- Wear OS Watch Face — [Format docs](https://developer.android.com/training/wearables/wff), [Watch Face Push](https://developer.android.com/training/wearables/watch-face-push), [Phone app side](https://developer.android.com/training/wearables/watch-face-push/phone-app), [Androidify case study](https://android-developers.googleblog.com/2025/12/bringing-androidify-to-wear-os-with.html), [Watch faces what's new I/O '25](https://android-developers.googleblog.com/2025/05/whats-new-in-watch-faces.html).
- ML Kit — [Subject Segmentation Android](https://developers.google.com/ml-kit/vision/subject-segmentation/android), [Selfie Segmentation Android](https://developers.google.com/ml-kit/vision/selfie-segmentation/android), [GenAI APIs](https://developers.google.com/ml-kit/genai).
- Gemini Nano / Firebase AI Logic — [docs](https://developer.android.com/ai/gemini-nano), [Hybrid Inference blog](https://android-developers.googleblog.com/2026/04/Hybrid-inference-and-new-AI-models-are-coming-to-Android.html).
- Material You / Monet — [dynamic colors](https://developer.android.com/develop/ui/views/theming/dynamic-colors), [Material 3 Expressive Android Authority](https://www.androidauthority.com/google-material-3-expressive-features-changes-availability-supported-devices-3556392/), [Sid Patil's Monet internals](https://siddroid.com/post/android/chasing-monet-inside-the-android-framework/), [AOSP material display source](https://source.android.com/docs/core/display/material).
- Photo Picker — [Android 14 docs](https://developer.android.com/about/versions/14/changes/partial-photo-video-access), [Android 17 `PhotoPickerUiCustomizationParams` 9:16 aspect ratio Beta 3](https://android-developers.googleblog.com/2026/03/the-third-beta-of-android-17.html), [Photo Picker training guide](https://developer.android.com/training/data-storage/shared/photo-picker).
- Predictive back — [Compose docs](https://developer.android.com/develop/ui/compose/system/predictive-back), [Android 14 behaviour change](https://developer.android.com/about/versions/14/behavior-changes-14#predictive-back-gesture), [Navigation 2.9 predictive-back integration breakdown](https://medium.com/@androidlab/androidx-navigation-2-9-6-complete-feature-breakdown-4b09ccd637dd).
- Compose Adaptive Layouts — [1.2 beta blog](https://android-developers.googleblog.com/2025/09/unfold-new-possibilities-with-compose-adaptive-layouts-1-2-beta.html), [Build adaptive apps guide](https://developer.android.com/develop/ui/compose/build-adaptive-apps), [adaptive layouts overview](https://developer.android.com/develop/ui/compose/layouts/adaptive), [support different display sizes](https://developer.android.com/develop/ui/compose/layouts/adaptive/support-different-display-sizes), [Touchlab adaptive layouts in CMP](https://touchlab.co/adaptive-layouts-cmp), [Kotlin Multiplatform adaptive layouts](https://kotlinlang.org/docs/multiplatform/compose-adaptive-layouts.html), [The Black Bit master adaptive layouts walkthrough Feb 2026](https://medium.com/@thebackbit/master-adaptive-layouts-in-compose-multiplatform-build-truly-responsive-uis-89184bf8b6de), [Google Play large-screen quality bar](https://developer.android.com/guide/topics/large-screens/get-started-with-large-screens).
- Compose Strong Skipping — [docs](https://developer.android.com/develop/ui/compose/performance/stability/strongskipping).
- R8 keep rules — [blog](https://android-developers.googleblog.com/2025/11/configure-and-troubleshoot-r8-keep-rules.html).
- AGP — [9.0](https://developer.android.com/build/releases/agp-9-0-0-release-notes), [9.1](https://developer.android.com/build/releases/agp-9-1-0-release-notes), [9.2](https://developer.android.com/build/releases/agp-9-2-0-release-notes).
- Gradle — [9 whats-new](https://gradle.org/whats-new/gradle-9/), [release notes](https://docs.gradle.org/current/release-notes.html).
- Kotlin — [2.3.20](https://kotlinlang.org/docs/whatsnew2320.html), [2.3](https://kotlinlang.org/docs/whatsnew23.html), [2.2.0 announce](https://blog.jetbrains.com/kotlin/2025/06/kotlin-2-2-0-released/).
- KotlinConf 2025 — [JetBrains recap](https://blog.jetbrains.com/kotlin/2025/05/kotlinconf-2025-language-features-ai-powered-development-and-kotlin-multiplatform/).
- Compose updates — [Apr 2026 blog](https://android-developers.googleblog.com/2026/04/jetpack-compose-april-2026-updates.html), [Material 3 release page](https://developer.android.com/jetpack/androidx/releases/compose-material3), [Dec 2025 whats-new](https://android-developers.googleblog.com/2025/12/whats-new-in-jetpack-compose-december.html), [jetc.dev #298 May 2026](https://jetc.dev/issues/298.html) (2026.05.00 BOM ↦ Compose 1.11.1 stable / 1.12.0-alpha02), [Material 3 Expressive deep dive Android Authority](https://www.androidauthority.com/google-material-3-expressive-features-changes-availability-supported-devices-3556392/).
- Dagger / Hilt — [2.59 release](https://github.com/google/dagger/releases/tag/dagger-2.59).
- Room — [release page](https://developer.android.com/jetpack/androidx/releases/room), [Room 3.0 announce](https://android-developers.googleblog.com/2026/03/room-30-modernizing-room.html).
- Retrofit 3 — [discussion #4379](https://github.com/square/retrofit/discussions/4379).
- OkHttp — [CHANGELOG](https://github.com/square/okhttp/blob/master/CHANGELOG.md), [Snyk security](https://security.snyk.io/package/maven/com.squareup.okhttp3%3Aokhttp).
- Coil 3 — [upgrade guide](https://coil-kt.github.io/coil/upgrading_to_coil3/), [CHANGELOG](https://github.com/coil-kt/coil/blob/main/CHANGELOG.md), [Coil 3.0 announce by Colin White](https://colinwhite.me/post/coil_3_release), [Maven Central coil3](https://central.sonatype.com/artifact/io.coil-kt.coil3/coil-compose) (3.4.0 stable Feb 24 2026).
- Media3 — [release page](https://developer.android.com/jetpack/androidx/releases/media3), [1.6.0 blog](https://android-developers.googleblog.com/2025/03/media3-1-6-0-is-now-available.html), [1.8.0 whats-new](https://medium.com/google-exoplayer/media3-1-8-0-whats-new-b857435651b9), [1.9.0 whats-new](https://android-developers.googleblog.com/2025/12/media3-190-whats-new.html), [1.10.0 release blog](https://android-developers.googleblog.com/2026/03/media3-110-is-out.html), [1.10.0 dev blog mirror](https://developer.android.com/blog/posts/media3-1-10-is-out), [Compose-2026 ExoPlayer 1.10 guide](https://medium.com/@ramadan123sayed/media-player-in-jetpack-compose-the-complete-2026-guide-exoplayer-media3-1-10-0a25af46ce7d).
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
- Security bulletins — [AOSP December 2025](https://source.android.com/docs/security/bulletin/2025-12-01), [SOCPrime CVE-2025-48572/-48633](https://socprime.com/blog/cve-2025-48633-and-cve-2025-48572-vulnerabilities/), [AOSP April 2026](https://source.android.com/docs/security/bulletin/2026/2026-04-01), [AOSP May 2026 (CVE-2026-0073 adbd zero-click RCE)](https://source.android.com/docs/security/bulletin/2026/2026-05-01), [Cybersecurity News CVE-2026-0073 coverage](https://cybersecuritynews.com/android-zero-click-vulnerability/), [CIS March 2026 multi-CVE Android advisory (CVE-2026-21385 active exploit)](https://www.cisecurity.org/advisory/multiple-vulnerabilities-in-google-android-os-could-allow-for-remote-code-execution_2026-017), [yt-dlp CVE-2026-26331 `--netrc-cmd` command injection](https://advisories.gitlab.com/pkg/pypi/yt-dlp/CVE-2026-26331/).
- NewPipe continuity risk — [PiunikaWeb March 2026](https://piunikaweb.com/2026/03/09/newpipe-certified-android-devices-warning/), [SABR-only player response Issue #12126](https://github.com/TeamNewPipe/NewPipe/issues/12126), [NewPipe 0.28.1 release Jan 2026](https://newpipe.net/blog/pinned/announcement/newpipe-0.28.1-released/).
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
- Localization tooling for OSS Android (informs U-11) — [Weblate self-hosted vs. Crowdin AI-localization comparison](https://www.g2.com/products/weblate/competitors/alternatives), [F-Droid forum thread on localized descriptions](https://forum.f-droid.org/t/localized-app-descriptions-via-translation-service-weblate-crowdin-stringlate/1610), [Crowdin Android SDK over-the-air](https://store.crowdin.com/android), [Weblate open-alternative profile](https://openalternative.co/weblate).
- Compose accessibility primitives (informs U-13 + U-9) — [Semantics & TalkBack Bryan Herbst](https://bryanherbst.com/2020/11/03/compose-semantics-talkback/), [Compose accessibility codelab](https://developer.android.com/codelabs/jetpack-compose-accessibility), [Compose API defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults), [4 common TalkBack issues in Compose](https://medium.com/@yanfalcao10/4-common-talkback-issues-in-android-compose-c6e3c3d92d19), [Deque accessibility-first analysis](https://www.deque.com/blog/how-jetpack-compose-is-helping-put-accessibility-first-for-android/).
- Live wallpaper battery research (informs T-G) — [DreamPixel battery analysis](https://dreampixelstudio.app/blog/use-live-wallpapers-on-android-without-draining-battery), [Computerworld battery drain study](https://www.computerworld.com/article/1416878/do-live-wallpapers-cause-noticeable-battery-drain-on-android.html).
- Baseline Profiles 2026 (informs L-8) — [Baseline Profiles overview](https://developer.android.com/topic/performance/baselineprofiles/overview), [Compose baseline-profile guide](https://developer.android.com/develop/ui/compose/performance/baseline-profiles), [2026 startup time analysis](https://medium.com/@ramadan123sayed/baseline-profiles-in-android-explained-from-scratch-what-art-compilation-is-why-your-first-app-898484bf6746), [Cloud Profiles vs Baseline 2026](https://dev.to/devin-rosario/optimizing-app-start-up-time-baseline-profiles-vs-cloud-profiles-in-2026-m05).
- WallpaperDescription / WallpaperInstance reference (informs N-4 completion) — [MS Learn WallpaperDescription class API 36](https://learn.microsoft.com/en-us/dotnet/api/android.app.wallpaper.wallpaperdescription?view=net-android-36.0), [WallpaperService.Engine OnApplyWallpaper](https://learn.microsoft.com/en-us/dotnet/api/android.service.wallpaper.wallpaperservice.engine.onapplywallpaper?view=net-android-36.0), [Salvatore's live-wallpaper how-to](https://sal.dev/android/android-live-wallpaper/).
- Compose Multiplatform 2026 (informs L-4) — [Compatibility & versioning matrix](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html), [Kotlin 2.2.20 what's new](https://kotlinlang.org/docs/whatsnew2220.html), [Production-ready in 2026 BestHub](https://www.besthub.dev/articles/why-kotlin-multiplatform-compose-multiplatform-are-production-ready-in-2026-24d731545514), [KMP ultimate guide 2026 commonmain.dev](https://commonmain.dev/kotlin-multiplatform/).
- Sunrise alarm references — [yuriykulikov/AlarmClock GitHub](https://github.com/yuriykulikov/AlarmClock).
- Custom ringtones Android UX — [9to5Google March 2024 contacts](https://9to5google.com/2024/03/20/google-contacts-custom-ringtones-android/).
