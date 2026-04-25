# Iter 1 — Scored Candidates (Phases 2 + 3)

Six-axis scoring (0–5 each) per directive-roadmap-research. Tier assignment: Now / Next / Later / Under Consideration / Rejected.

## Scoring axes
- **Fit** — alignment with charter (personalization, dark-first, OSS Android)
- **Impact** — magnitude of user-facing improvement / reliability gain
- **Effort** — inverse: 5 = small, 1 = huge
- **Risk** — inverse: 5 = safe, 1 = scary
- **Dependencies** — inverse: 5 = self-contained, 1 = many external
- **Novelty** — does this beat the OSS landscape on something concrete?

Total = sum (max 30). ≥24 = Now, 18–23 = Next, 12–17 = Later, <12 = Under Consideration, charter violation = Rejected.

---

## NOW (this iteration)

### T-1: Freesound 429 / Retry-After bounded backoff
- Source: ROADMAP Round 3 pitfall + verified against `FreesoundV2Repository.kt` and `AppModule.provideOkHttpClient()`. Issue is real, no current handling.
- Plan: add bounded-retry interceptor (max 2 retries, honor `Retry-After` header up to 30 s ceiling) wired to Freesound base URL only — don't penalize other APIs.
- Fit 5 / Impact 4 / Effort 4 / Risk 5 / Deps 5 / Novelty 3 = **26 → NOW**

### T-2: Palette best-accent fallback ladder
- Source: ROADMAP Round 3 pitfall + verified `ColorExtractor.extractFromBitmap()` doesn't expose ladder, leaves it to consumers.
- Plan: compute `bestAccentColor` server-side in ColorExtractor: dominant if saturation ≥ 0.20 AND HSL.lightness ∈ [0.18, 0.82]; else darkVibrant → vibrant → lightVibrant → mutedDark → muted → mutedLight → dominant; expose to consumers.
- Fit 5 / Impact 3 / Effort 4 / Risk 5 / Deps 5 / Novelty 3 = **25 → NOW**

### T-3: Cancellation-rethrow audit pass (round 19, follow-up to v6.10.0)
- Source: prior audit cadence. Round 18 closed widget callbacks + `evictStaleCaches`. Open question: any newer catch sites that swallow `CancellationException`?
- Plan: scan all `catch (.*: Exception)` blocks across ViewModels + repositories + services that don't rethrow CancellationException; fix any found; add unit-test pattern for one of them.
- Fit 5 / Impact 3 / Effort 4 / Risk 5 / Deps 5 / Novelty 2 = **24 → NOW**

---

## NEXT (queued, candidate for next factory run)

### T-4: NewPipe stream-leak re-verify
- Source: ROADMAP Round 3 pitfall ("re-verify on every upstream bump").
- Plan: scan `YouTubeRepository.kt` + `service/SoundUrlResolver.kt` for InputStream / BufferedReader without `.use { }`. Fix any. Pin NewPipeExtractor version comment.
- Fit 5 / Impact 3 / Effort 4 / Risk 5 / Deps 4 / Novelty 1 = 22 → NEXT (would have been NOW but T-3 covers similar ground; defer to keep scope tight)

### T-5: Wallhaven SafeSearch toggle
- Source: ROADMAP Round 2 (WallFlow / WallCraft "Features to Borrow").
- Plan: add user-facing toggle in SettingsScreen → API tab; pipe `purity=100` (SFW only) as default vs. `purity=110` (include sketchy) opt-in; never include `111` (NSFW) without explicit + audit.
- Fit 5 / Impact 4 / Effort 3 / Risk 4 / Deps 5 / Novelty 2 = 23 → NEXT

### T-6: Per-source result-count telemetry (in-app diagnostics)
- Source: prior audit notes about silent fallbacks (Reddit / Pexels / etc.).
- Plan: developer-only Settings → Diagnostics tab showing last-N source request counts + success/failure ratio + per-source quota state.
- Fit 4 / Impact 3 / Effort 3 / Risk 5 / Deps 5 / Novelty 3 = 23 → NEXT

### T-7: Wallpaper auto-rotation per-album charging-only / Wi-Fi-only constraints
- Source: Paperize feature borrow (Round 2). `AutoWallpaperWorker` already exists; missing constraints.
- Plan: add `requiresCharging`, `requiredNetworkType=UNMETERED` to per-album rotation worker; expose toggles in collection settings.
- Fit 5 / Impact 4 / Effort 3 / Risk 4 / Deps 5 / Novelty 1 = 22 → NEXT

---

## LATER

### T-8: Plugin / source ABI (Muzei-style)
- Big-effort architectural work. Defer until plugin demand is concrete.
- Fit 4 / Impact 4 / Effort 1 / Risk 2 / Deps 3 / Novelty 5 = 19 → LATER

### T-9: GLWallpaperService migration for video wallpapers
- Source: AlynxZhou/alynx-live-wallpaper reference. Replaces MediaPlayer with ExoPlayer + OpenGL ES.
- Higher battery efficiency; correct audio focus; bigger refactor.
- Fit 4 / Impact 4 / Effort 1 / Risk 2 / Deps 2 / Novelty 4 = 17 → LATER

### T-10: Firebase RTDB → Firestore migration for community votes
- Source: Round 3 pitfall about RTDB quota.
- No telemetry showing we're hitting the cap. Park.
- Fit 3 / Impact 3 / Effort 1 / Risk 2 / Deps 3 / Novelty 2 = 14 → LATER

---

## REJECTED

### R-1: AI on-device wallpaper generation
- Source: ROADMAP Phase 3.1
- Charter contradiction: Aura has explicitly pruned AI-generation in v5.0.0. Re-enabling needs user / charter call, not factory autopilot.
- Tagged CHARTER-REVIEW; deferred until owner re-litigates the charter.

### R-2: Freesound OAuth2 for full (non-preview) audio
- Source: Round 3 note.
- Adds complete user-account flow + token refresh storage. Disproportionate to current Aura value (preview HQ MP3 is already 128 kbps and matches typical ringtone fidelity). Reject unless users explicitly request full-quality download.

---

## Tier summary
- NOW (this run): T-1, T-2, T-3
- NEXT: T-4, T-5, T-6, T-7
- LATER: T-8, T-9, T-10
- Rejected: R-1, R-2 (with justification preserved so they don't silently resurrect)

**Single-session degradation note:** Phase 5 self-audit skipped — same model would judge own scoring, no signal. Tier assignments stand on this pass.
