# Changelog

All notable changes to Aura will be documented in this file.

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
