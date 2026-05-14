
<p align="center">
  <img src="Aura-Logo.png" width="128" alt="Aura">
</p>

<h1 align="center">Aura</h1>

![Version](https://img.shields.io/badge/version-6.31.0-blue)
![License](https://img.shields.io/badge/license-MIT-green)
![Platform](https://img.shields.io/badge/platform-Android%208.0+-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4)

> Open-source alternative to Zedge — wallpapers, video wallpapers, ringtones, and sounds for Android. **YouTube integration, yt-dlp powered.**

![Aura Screenshot](screenshot.png)

## What Makes Aura Different

- **Quality-ranked YouTube sounds** — ringtones, notifications, and alarms use intent-specific YouTube searches with tight duration windows and cleaner result filtering.
- **Video wallpapers from multiple sources** — browse Reddit, Pexels, Pixabay, and YouTube, import local videos/GIFs, then tune loop, crop, Fill, or Fit before applying.
- **Multi-source personalization** — Wallhaven, Bing, Pexels, Pixabay, Reddit, YouTube, legacy Freesound attributions, and community uploads.
- **Instant startup** — Discover feed is cached locally. On subsequent launches wallpapers appear immediately while fresh results load in the background.
- **5 bottom nav tabs** — Wallpapers, Videos, Sounds, Favorites, Settings.

## Quick Start

```bash
git clone https://github.com/SysAdminDoc/Aura.git
cd Aura
```

Open in Android Studio and run. Core browsing works out of the box; optional provider keys can be added later in Settings or `local.properties`.

## Features

| Feature | Description |
|---------|-------------|
| **HD/4K Wallpapers** | Discover feed from Wallhaven, Pexels, Pixabay, Bing & Reddit |
| **Wallpaper Quality Filters** | Discover chips for For You, AMOLED, 4K+, Portrait, and Icon Safe with curated ranking |
| **Community Wallpapers** | Upload phone-cropped gallery images with tags, Palette colors, and community voting |
| **Creator Profiles** | View upload stats, votes, followed creators, followed uploads, and top creator leaderboard |
| **Shareable Collections** | Share wallpaper collections as Aura links, QR codes, or JSON files and import them on another device |
| **Video Wallpapers** | Browse YouTube video wallpapers with ExoPlayer auto-preview or import local clips/GIFs |
| **Video Quality Hints** | Loop-safe, low-battery, and phone-fit filters plus per-card motion hints |
| **Video Fit Modes** | Fill for full-screen crop or Fit to preserve the full frame |
| **Video Loop & Crop Editor** | Trim intros/outros with frame thumbnails, preview the loop, and convert landscape videos to portrait |
| **Video Battery Dashboard** | Live wallpaper-service heartbeat, battery status, effective FPS, and automatic low-battery capping |
| **Parallax Wallpapers** | ML Kit depth segmentation for layered tilt-responsive live wallpapers |
| **Weather Wallpapers** | Live weather effects overlay on wallpapers |
| **Touch-Reactive Effects** | Optional ripple and sparkle bursts on live wallpaper touches |
| **YouTube Sounds** | YouTube-first ringtone, notification, and alarm discovery with duration-aware searches powered by NewPipe + yt-dlp |
| **Community Sound Uploads** | Pick or record sounds, tag them, vote on community picks, and share via Firebase Storage |
| **Sound Source Badges** | Color-coded source indicators on every sound card |
| **Sound Quality Filters** | Best, Clean, Short, Calm, and Punchy filters with intent-aware badges |
| **Real-Time Waveform** | Mini waveform on each sound card tracks actual playback position |
| **Configurable Search** | Customize YouTube search queries and blocked words per sound tab |
| **Ringtones & Sounds** | Tab-based browsing: Ringtones (5-45s), Notifications (0-8s), Alarms (5-60s) |
| **Sound Editor** | Waveform trim, fade in/out, normalize, format convert (MP3/OGG/WAV/FLAC/M4A) |
| **Wallpaper Editor** | Brightness, contrast, saturation, blur with 6 filter presets |
| **Crop & Position** | Pinch-zoom with aspect ratio presets (9:16, 16:9, 1:1) |
| **Collections** | Organize wallpapers into named folders with 2x2 cover previews |
| **Home Widget** | Glance-based widget for quick shuffle with error feedback |
| **Auto Wallpaper** | Rotation schedule + source selection including favorites |
| **Shuffle FAB** | One-tap random wallpaper from current tab |
| **Per-Contact Ringtones** | Assign custom ringtones to individual contacts |
| **Dual Wallpapers** | Coordinated home + lock screen wallpaper pairs |
| **Favorites Export** | JSON export/import with full metadata via Android SAF |
| **Community Voting** | Upvote/downvote wallpapers and sounds via Firebase |
| **OLED Dark Theme** | Deep blacks, zero burn-in, Material 3 |

## Content Sources

| Source | Content | Auth |
|--------|---------|------|
| [Wallhaven](https://wallhaven.cc) | 1M+ HD/4K wallpapers | None (optional key for NSFW) |
| [Pexels](https://pexels.com) | Curated HD photos + videos | Built-in key |
| [Pixabay](https://pixabay.com) | Editor's choice photos + videos | Built-in key |
| [Reddit](https://reddit.com) | 7 wallpaper + 4 video subreddits | None |
| [YouTube](https://youtube.com) | Video wallpapers + active sound feed via NewPipe + yt-dlp | None |
| [Freesound](https://freesound.org) | Legacy sound attribution for older favorites | Built-in key |
| Firebase | Community wallpaper/sound uploads + voting | Built-in |

## Architecture

```
Jetpack Compose UI (16+ screens, 5 bottom nav tabs)
  Wallpapers | Videos | Sounds | Favorites | Settings
  Editors | Collections | Downloads | Onboarding | Widget
ViewModels (Hilt) + Cache Layer
  Repos: Wallhaven, Pexels, Pixabay, Bing, Reddit, YouTube, Freesound legacy,
         Collections
  Services: WallpaperApplier, SoundApplier, VideoWallpaperService,
            ParallaxWallpaperService, WeatherWallpaperService, DualWallpaperService,
            DownloadManager, AudioTrimmer, BatchDownload,
            ContactRingtone, FavoritesExporter, OfflineFavorites
  YouTube: NewPipe Extractor (search) + yt-dlp (stream extraction + FFmpeg crop)
Room DB v14 (Favorites, Downloads, Search History, Wallpaper Cache,
            Wallpaper History, Collections)
DataStore (Settings, Onboarding)
Firebase RTDB (Community Voting + Uploads + Admin Moderation)
```

## Tech Stack

| Component | Library |
|-----------|---------|
| UI | Jetpack Compose + Material 3 |
| DI | Hilt 2.53.1 |
| Database | Room 2.6.1 |
| Network | Retrofit 2.11.0 + OkHttp |
| JSON | Moshi + KSP codegen |
| Images | Coil 2.7.0 |
| Audio/Video | Media3 ExoPlayer |
| ML | ML Kit Selfie Segmentation |
| YouTube Search | NewPipe Extractor |
| YouTube Streams | yt-dlp (youtubedl-android 0.18.1) |
| Scheduling | WorkManager 2.10.0 |
| Widget | Glance 1.1.1 |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 (Android 15) |
| Kotlin | 2.1.0 |

## Building

Requires JDK 17+ and Android SDK 35. Android Studio Ladybug (2024.2.1) or later recommended.

```bash
./gradlew assembleDebug      # use gradlew.bat on Windows
./gradlew assembleRelease     # requires signing config
```

> Always use the included Gradle wrapper. It pins Gradle 8.12 which is required by AGP 8.7.3.

## Contributing

Issues and PRs welcome. Please follow existing code style (Kotlin, Compose, Hilt patterns).

## License

MIT License - see [LICENSE](LICENSE) for details.

Content from third-party sources retains its original license. YouTube content is accessed via NewPipe Extractor and yt-dlp under their respective open-source licenses.
