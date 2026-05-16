# Aura Originals — Curation Guide

Roadmap N-5. Goal: 200–500 high-quality CC0/public-domain sounds bundled
with Aura's first-run experience so users never see an empty Sounds tab.

The bundle is **not** in the APK. It is downloaded on first launch over
Wi-Fi by `AuraOriginalsDownloader` from the URLs listed in
[`app/src/main/assets/aura_originals_manifest.json`](../app/src/main/assets/aura_originals_manifest.json).
Each file is sha256-verified before it is moved to its final location,
so a CDN that changes a file under us is detected and the entry is
re-tried on the next worker run.

## Target distribution

| Category     | Count   | Duration window | Notes                                     |
|--------------|---------|-----------------|-------------------------------------------|
| Ringtone     | 100+    | 8–30 s          | Melodic, minimal, electronic, marimba…    |
| Notification | 100+    | 1–5 s           | Chimes, pings, dings, clicks, pops        |
| Alarm        | 100+    | 10–40 s         | Gentle wake, buzzer, nature, musical      |
| SFX bonus    | 50+     | varies          | UI clicks, transition effects             |

## Manifest schema

```jsonc
{
  "version": 1,
  "manifestRevision": "YYYY-MM-DD-revN",
  "totalBytes": <sum-of-all-file-sizes>,
  "sounds": [
    {
      "id": "ring_crystal_bloom_01",
      "category": "ringtone",      // ringtone | notification | alarm
      "name": "Crystal Bloom",
      "durationSec": 14.2,
      "url": "https://freesound.org/data/previews/411/411089-hq.mp3",
      "sha256": "<lowercase-hex-of-the-actual-file-bytes>",
      "license": "CC0 1.0",
      "sourceUrl": "https://freesound.org/s/411089/",
      "tags": ["ringtone", "chime", "crystal"]
    }
  ]
}
```

## Curation workflow

1. **Source check.** Search Freesound for `license:"Creative Commons 0"`
   sorted by `rating_desc` and `downloads_desc`. Stick to CC0; do not mix
   in CC-BY without an attribution UI.
2. **Listen test.** Skip anything with loudness clipping, audible cuts,
   or background noise.
3. **Duration window.** Trim externally if needed; aim for the windows
   in the table above. Keep the original in your working folder for
   provenance.
4. **Normalize.** Run loudnorm so the pack feels even — Aura's preview
   ExoPlayer doesn't apply gain compensation.
5. **Hash.** `shasum -a 256 <file>` and paste the lowercase hex into the
   manifest entry.
6. **Stage.** Upload the normalized file to a stable CDN. Freesound's
   own `data/previews/` URLs are stable but re-encoded; if absolute
   fidelity matters, host the normalized originals yourself.
7. **Bump `manifestRevision`** to the date + a monotonic counter.
8. **Commit + PR.** CI should verify each entry's URL is reachable (a
   HEAD probe) and that the manifest's `totalBytes` matches the sum
   of declared `durationSec * 16 kBps` approximations within ±25 %.

## License compliance

CC0 is permissive but the dedication does not protect against later DMCA
re-uploads. Two safety measures live in this codebase:

- Each entry carries its `sourceUrl` so we can verify the original
  uploader's CC0 dedication on Freesound's moderation log.
- The sha256 manifest gives us retroactive removal: if a file is later
  found to be miscategorized, drop its entry from the manifest and the
  downloader's `verifyHash` mismatch will quietly fail (Aura keeps the
  prior copy but no longer ships it to new installs).

## Storage layout

Downloaded files land in `filesDir/aura_originals/<id>.<ext>`. Aura's
`BundledContentProvider` consults this directory at runtime; entries
present on disk are returned as `ContentSource.BUNDLED` `Sound`s with a
local `file://` URI. Entries missing on disk fall back to the
URL-backed `BundledContentProvider` defaults so the Sounds tab is never
empty even before the worker completes.

## Empty manifest = scaffolding-only

`aura_originals_manifest.json` ships with `"sounds": []` until the
curation pass completes. The downloader honors empty manifests with a
zero-work success. Aura's existing URL-backed `BundledContentProvider`
catalog (10 ringtones, 10 notifications, 5 alarms) remains the
fallback during this window.
