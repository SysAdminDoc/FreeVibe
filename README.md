# FreeVibe

![Version](https://img.shields.io/badge/version-1.0.0-blue)
![License](https://img.shields.io/badge/license-MIT-green)
![Platform](https://img.shields.io/badge/platform-Android%208.0+-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4)

> Open-source Android app for personalizing your device with wallpapers, ringtones, and sounds from 7 free sources. **No API keys required. Install and go.**

## Quick Start

```bash
git clone https://github.com/SysAdminDoc/FreeVibe.git
cd FreeVibe
```

Open in Android Studio Ladybug+ and run. Everything works out of the box.

## Features

| Feature | Description |
|---------|-------------|
| HD/4K Wallpapers | Browse from Wallhaven, Unsplash, Bing & Reddit |
| Discover Feed | Mixed wallpapers from all sources in one shuffled stream |
| Phone Preview | See wallpaper on device mockup with clock/status bar before applying |
| Color Search | Find wallpapers by dominant color via Wallhaven palette |
| Ringtones & Sounds | 600K+ sounds from Freesound + Internet Archive with duration filtering |
| Sound Categories | Browse by Nature, Electronic, Funny, Scary, Sci-Fi, Musical, Ambient & more |
| Duration Filters | Filter sounds by length: short (<5s), medium (5-15s), long (15-60s) |
| More Like This | Discover acoustically similar sounds via Freesound |
| Wallpaper Editor | Brightness, contrast, saturation, blur adjustments |
| Sound Editor | Waveform visualization, lossless trim, fade in/out effects |
| Create from File | Open any audio file from your device to make a ringtone |
| Crop & Position | Pinch-zoom + pan wallpaper before applying |
| Per-Contact Ringtones | Assign custom ringtones to individual contacts |
| Dual Wallpapers | Coordinated home + lock screen wallpaper pairs |
| Home Widget | Glance-based widget for quick shuffle/apply |
| Auto Wallpaper | Configurable rotation schedule + source selection via WorkManager |
| Wallpaper History | Track and revisit previously applied wallpapers |
| Video Wallpaper | Live wallpaper service for video files |
| Categories | 16 curated wallpaper categories with gradient cards |
| Download Manager | MediaStore downloads with progress tracking |
| Batch Download | Concurrent multi-wallpaper download with throttling |
| Offline Favorites | Favorited content cached locally for offline access |
| Favorites Export | JSON export/import via Android SAF |
| Search History | Recent queries with autocomplete |
| Pull-to-Refresh | Swipe down to reload on all content screens |
| Mini Waveform | Visual duration indicator on sound cards |
| OLED Dark Theme | Deep blacks, zero burn-in, Material 3 dynamic color |

## Content Sources

| Source | Content | Auth | License |
|--------|---------|------|---------|
| [Wallhaven](https://wallhaven.cc) | 1M+ HD/4K wallpapers | None (optional key for NSFW) | Various per image |
| [Lorem Picsum](https://picsum.photos) | Curated Unsplash photos | None | Unsplash License |
| [Bing Daily](https://www.bing.com) | Curated daily photos, UHD, 10 markets | None | Wallpaper use |
| [Reddit](https://reddit.com) | 11 wallpaper subreddits | None | User-owned |
| [Freesound](https://freesound.org) | 600K+ tagged sound effects | Free API key | CC0 / CC-BY / CC-BY-NC |
| [Internet Archive](https://archive.org) | Millions of audio clips | None | CC / Public Domain |

## Architecture

```
Jetpack Compose UI
  Wallpapers | Sounds | Favorites | Settings | Editors
  Onboarding | Categories | Downloads | Glance Widget
ViewModels (Hilt) + Cache Layer
  Repositories: Wallhaven, Picsum, Bing, Reddit, Freesound, Internet Archive
  Services: WallpaperApplier, SoundApplier, DownloadManager,
            AudioTrimmer, DualWallpaper, BatchDownload,
            ContactRingtone, FavoritesExporter,
            OfflineFavorites, WallpaperHistory
Room DB (Favorites, Downloads, Search History, Wallpaper Cache,
         Wallpaper History, IA Audio Cache)
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
| Audio | Media3 ExoPlayer |
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

## F-Droid

Fastlane metadata is included in `fastlane/metadata/android/en-US/`.

## Contributing

Issues and PRs welcome. Please follow existing code style (Kotlin, Compose, Hilt patterns).

## License

MIT License - see [LICENSE](LICENSE) for details.

Content from third-party sources retains its original license.
