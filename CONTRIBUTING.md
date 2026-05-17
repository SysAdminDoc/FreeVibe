# Contributing to Aura

Thanks for the interest. Aura is an open-source Android personalization app — wallpapers, video wallpapers, ringtones, sounds, AI art — and patches that fit the [charter](#charter) are welcome.

## Charter

Read this before opening a PR. Anything that conflicts with the charter will be closed.

- **Personalization first** — wallpapers, video wallpapers, ringtones, notifications, alarms, parallax, weather, AI art. Adjacent features are fine; tangents (note-taking, gallery management, photo editor) are not.
- **AMOLED-first** — deep blacks, true zero burn-in. Every new surface ships with a dark theme; light theme is a follow-up, never a launch blocker.
- **Free by default** — no ads, no subscriptions, no coins, no streaks, no surprise charges. Donations via GitHub Sponsors / Liberapay are the only acceptable monetization vector.
- **Multi-source aggregation** — Wallhaven, Pexels, Pixabay, Reddit, Bing, YouTube, Firebase-backed community uploads. New sources land behind a `WallpaperRepository` or `SoundRepository` interface; no source-specific code in ViewModels.
- **Polite live wallpapers** — pause on invisible, cap FPS on low battery, honor user-toggled effects. Battery dashboard is the live spec.
- **No tracking, no account required** — all preferences in `DataStore` (local). Firebase voting + uploads use anonymous identity. Optional Google sign-in is opt-in only.

If your PR contradicts the charter and you think the charter is wrong, open an issue first to discuss.

## Build

Requires JDK 17+ and Android SDK 35. Android Studio Ladybug (2024.2.1) or later.

```bash
./gradlew assembleDebug      # use gradlew.bat on Windows
./gradlew testDebugUnitTest  # unit tests (49 files at the time of writing)
./gradlew lintDebug
./gradlew assembleRelease    # requires signing config in local.properties
```

CI runs `assembleDebug` + `testDebugUnitTest` + `lintDebug` on every push to `main` and every PR via [`.github/workflows/verify.yml`](.github/workflows/verify.yml). Branch protection requires the verify check to pass before merge.

Gradle wrapper is pinned to 8.12. AGP 8.7.3. Kotlin 2.1.0. JDK 17. See [`gradle/wrapper/gradle-wrapper.properties`](gradle/wrapper/gradle-wrapper.properties) and [`app/build.gradle.kts`](app/build.gradle.kts).

`local.properties` example:

```
sdk.dir=/Users/you/Library/Android/sdk
pexels.api.key=    # optional — Aura ships a default
pixabay.api.key=   # optional
freesound.api.key= # optional
soundcloud.client.id= # optional
stability.ai.key= # optional — user-supplied for AI wallpaper generation
signing.keystore.path=../freevibe.jks  # release builds only
signing.keystore.password=
signing.key.alias=freevibe
signing.key.password=
```

## Architecture

See [`ARCHITECTURE.md`](ARCHITECTURE.md) for the layered overview, package map, and contribution patterns.

## Branches & PRs

- `main` is the only long-lived branch. Branch off, name `feat/<short-slug>` or `fix/<short-slug>`, open a PR.
- Squash on merge unless the history is meaningful (multiple atomic refactors).
- Each PR description must include:
  - **What changed and why** (one paragraph)
  - **Roadmap reference** if applicable — `[ROADMAP.md](ROADMAP.md)` item ID (e.g. `NX-3`, `L-2`)
  - **Screenshots** for UI changes — capture at 125 % DPI on a 1080×2400 viewport
  - **Test plan** — what you ran, what you checked

## Code style

- **Kotlin idiomatic** — `val` over `var`, `?.let` over null checks, `runCatching` over try/catch when the failure mode is uniform, structured concurrency (always rethrow `CancellationException`).
- **Compose** — `collectAsStateWithLifecycle()` over `collectAsState`. `rememberSaveable` for gesture / scroll state. `derivedStateOf` for computed UI values.
- **Hilt** — every singleton-scoped service in `service/` and every repository in `data/repository/` gets `@Singleton`-annotated. ViewModels get `@HiltViewModel`. No manual `Component`s.
- **No `runBlocking` from UI handlers** — every entry point that reads `DataStore` is suspend or uses `viewModelScope.launch`. The one historical exception (`AutoWallpaperWorker.schedule`) became suspend in v6.12.0; do not re-introduce the pattern.
- **Locale.ROOT** — every machine-use `lowercase()` / `uppercase()` / `format()` call uses `Locale.ROOT`. Turkish locale dotless-i has cost us bugs before.
- **No pill / oval / fully-rounded backdrops in GUI work** — radii are `0`, `4`, `6`, `8`, `10`, or `12 dp`. Status badges differentiate via colour / border / font weight, not stadium shape. See repo CLAUDE.md for the design rule.
- **Image bounds** — every HTTP image fetch goes through a size cap. See `WallpaperApplier.MAX_WALLPAPER_BYTES` (64 MB), `ColorExtractor.MAX_EXTRACT_BYTES` (32 MB), `OfflineFavoritesManager.MAX_PER_FILE_BYTES` (80 MB). Use `readCapped` / `copyCapped` helpers when adding a new write path.
- **Recycle defensively** — `Bitmap.createBitmap` can return the same object; check `!==` before recycling.

## Tests

- Unit tests live in `app/src/test/java/com/freevibe/` and run via `testDebugUnitTest`. Pure-Kotlin code (geometry helpers, content filters, query builders) should land with tests; ViewModels can rely on integration coverage if mocking is heavy.
- Instrumented tests live in `app/src/androidTest/java/com/freevibe/` and run via `connectedDebugAndroidTest`. Required for any flow that touches MediaStore, Room migrations, or FFmpeg subprocess wiring.
- Screenshot tests (Paparazzi / Roborazzi) are queued under roadmap item U-13; held until N-1 toolchain settles.

## Commits

- One commit per logical change. No `wip:` or `chore: bump` commits — squash them locally before opening the PR.
- Conventional-commits-ish format: `feat(NX-3): smart crop with subject segmentation` / `fix(scheduler): rethrow CancellationException in worker schedule`.
- **No AI-attribution metadata in commit messages, code comments, README, CHANGELOG, ROADMAP, or any other tracked file.** `CLAUDE.md`, `CODEX_CHANGELOG.md`, and `.claude/` are gitignored.

## Roadmap

[`ROADMAP.md`](ROADMAP.md) is the source of truth for what's queued. Open issues against existing items by their ID. New items must hit the charter and ship with sources in the Appendix.

The roadmap uses **Now / Next / Later / Under Consideration / Rejected** tiers scored by `fit + impact + effort + risk + dependencies + novelty`. See the "How to read this document" section of the roadmap for tier thresholds.

## Plugins (Aura Sources)

Roadmap **NX-5** introduces a Muzei-compatible `MuzeiArtProvider` IPC contract. Once that lands, third-party sources can plug into Aura without forking. The contract will be versioned (`v1`) and documented under [`docs/plugins/`](docs/plugins/) — held for now.

## Code of conduct

Be polite. Issue tracker is for bugs and feature requests; flames go to `/dev/null`. Maintainers reserve the right to lock disrespectful threads.

## License

MIT — see [`LICENSE`](LICENSE). Contributions are licensed under the same terms.
