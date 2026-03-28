# FreeVibe

![Version](https://img.shields.io/badge/version-3.0.0-blue)
![License](https://img.shields.io/badge/license-MIT-green)
![Platform](https://img.shields.io/badge/platform-Android%208.0+-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4)

> Open-source alternative to Zedge — wallpapers, video wallpapers, ringtones, and sounds for Android. **YouTube integration, yt-dlp powered. Zero configuration. Install and go.**

## What Makes FreeVibe Different

- **YouTube-powered sounds** — search YouTube for ringtones, notifications, and alarms. NewPipe Extractor for search, yt-dlp for stream extraction. No API keys needed.
- **Video wallpapers from YouTube** — browse, preview with ExoPlayer auto-play, crop landscape to portrait, apply as live wallpaper.
- **Progressive loading** — sounds appear as they resolve instead of waiting for all results. YouTube results load instantly, Internet Archive streams in alongside.
- **5 bottom nav tabs** — Wallpapers, Videos, Sounds, Favorites, Settings — everything in one app.

## Quick Start

```bash
git clone https://github.com/SysAdminDoc/FreeVibe.git
cd FreeVibe
```

Open in Android Studio and run. Everything works out of the box.

## Features

| Feature | Description |
|---------|-------------|
| **HD/4K Wallpapers** | Discover feed from Wallhaven, Unsplash, Bing & Reddit |
| **Video Wallpapers** | Browse YouTube video wallpapers with ExoPlayer auto-preview |
| **Video Crop Editor** | Convert landscape videos to portrait with draggable 9:16 crop overlay |
| **YouTube Sounds** | Search YouTube for ringtones, notifications, alarms — powered by yt-dlp |
| **Internet Archive** | Thousands of CC/Public Domain sound effects with progressive loading |
| **Ringtones & Sounds** | Tab-based browsing: Ringtones (5-30s), Notifications (0-3s), Alarms (5-40s) |
| **Sound Editor** | Waveform trim, fade in/out, undo, presets (Warm/Cool/Vivid/Cinematic/Dreamy/B&W) |
| **Wallpaper Editor** | Brightness, contrast, saturation, blur with 6 filter presets |
| **Crop & Position** | Pinch-zoom with aspect ratio presets (9:16, 16:9, 1:1) |
| **Collections** | Organize wallpapers into named folders with 2x2 cover previews |
| **Home Widget** | Glance-based widget for quick shuffle with error feedback |
| **Auto Wallpaper** | Rotation schedule + source selection including favorites |
| **Shuffle FAB** | One-tap random wallpaper from current tab |
| **Parallax Detail** | Scale/translate/alpha effect when swiping between wallpapers |
| **Per-Contact Ringtones** | Assign custom ringtones to individual contacts |
| **Dual Wallpapers** | Coordinated home + lock screen wallpaper pairs |
| **Favorites Export** | JSON export/import via Android SAF |
| **Haptic Feedback** | Vibration on favorite toggle |
| **OLED Dark Theme** | Deep blacks, zero burn-in, Material 3 |

## Content Sources

| Source | Content | Auth |
|--------|---------|------|
| [Wallhaven](https://wallhaven.cc) | 1M+ HD/4K wallpapers | None |
| [Lorem Picsum](https://picsum.photos) | Curated Unsplash photos | None |
| [Bing Daily](https://www.bing.com) | Curated daily photos, UHD | None |
| [Reddit](https://reddit.com) | Wallpaper subreddits | None |
| [YouTube](https://youtube.com) | Video wallpapers + sounds via NewPipe Extractor + yt-dlp | None |
| [Internet Archive](https://archive.org) | CC/Public Domain audio clips | None |

## Architecture

```
Jetpack Compose UI (16 screens, 5 bottom nav tabs)
  Wallpapers | Videos | Sounds | Favorites | Settings
  Editors | Collections | Downloads | Onboarding | Widget
ViewModels (Hilt) + Cache Layer
  Repos: Wallhaven, Picsum, Bing, Reddit, YouTube, IA, Collections
  Services: WallpaperApplier, SoundApplier, VideoWallpaperService,
            DownloadManager, AudioTrimmer, DualWallpaper, BatchDownload,
            ContactRingtone, FavoritesExporter, OfflineFavorites
  YouTube: NewPipe Extractor (search) + yt-dlp (stream extraction + FFmpeg crop)
Room DB v4 (Favorites, Downloads, Search History, Wallpaper Cache,
            Wallpaper History, IA Audio Cache, Collections)
DataStore (Settings, Onboarding)
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
| YouTube Search | NewPipe Extractor |
| YouTube Streams | yt-dlp (youtubedl-android 0.18.1) |
| Scheduling | WorkManager 2.10.0 |
| Widget | Glance 1.1.1 |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 (Android 15) |
| Kotlin | 2.1.0 |

## Building

Requires Android Studio Ladybug (2024.2.1) or later with JDK 17+ and Android SDK 35.

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
