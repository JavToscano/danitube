# DaniTube

A private, offline video app for a toddler. Videos are downloaded once from YouTube, stored on the
phone, and played in a YouTube-style app with no ads, no recommendations and no links out. She can
only browse and scroll; everything else sits behind a parent PIN.

The interface is in Spanish. The code and this README are in English.

> **Personal use only.** Downloading from YouTube goes against YouTube's terms of service. This app
> is meant to be installed on your own family's phone. Don't publish it to the Play Store.

## Features

**For the child**
- **Home feed** (library videos, with suggested ones mixed in on Wi-Fi) with a large thumbnail, duration and title for every downloaded video.
- **Shorts-style player**: full screen, swipe up for the next video, tap to pause, no other
  controls. Videos are shuffled, and the next one starts by itself when one ends.
- **Portrait by default** with a rotate button (like YouTube's) to switch to landscape and back.
  Black bars, never cropping.
- **Favoritos tab**: the videos she plays most, most-watched first. A video counts as played after
  3 seconds on screen.
- **Search** over the downloaded videos (it never touches the internet).
- Looks like the video app she already knows: dark theme, red accent, bottom bar.
- Back never leaves the app from the home screen.

**For the parent** (press and hold the logo, then enter the PIN)
- **Suggested videos**: the app learns from the tags and channels of the library (videos she
  plays count more). Once a day, on Wi-Fi only, it searches YouTube for similar videos, skips live,
  age-restricted and long ones, and keeps the best as suggestions. Suggestions are streamed in the
  same feed and player, and up to 3 a day are downloaded into the library for good. Nothing streams
  or downloads on mobile data. The parent area has a switch, a *search now* button, and delete
  (a deleted suggestion is never suggested again).
- **Search and pick**: in *Agregar video*, type what she asked for (for example "gugu gaga") and
  tap *Buscar en YouTube*. A list of results appears with thumbnail, duration and channel; tick
  the ones you want and download them together. Videos already in the library are greyed out.
- **Agregar video**: paste a link, or use *Share → DaniTube* from the YouTube app or a browser.
  Works with single videos, Shorts links and whole playlists. Paste several links at once (one
  per line) to queue them all in one go. A preview of the first one is shown before downloading.
- **Download queue** with progress, cancel and retry.
- **Administrar videos**: list with delete.
- **Carpeta de videos**: pick a folder outside the app (USB, Downloads, a synced folder). Videos
  are stored there, not inside the app, so updates and uninstalls don't lose them. After a
  reinstall, pick the same folder and the library comes back.
- **Updates from GitHub**: once a day, on Wi-Fi only, the app checks the project's latest GitHub
  release and downloads the new APK. When one is ready, the parent area shows **Actualizar a vX.Y.Z**;
  tapping it installs it. Android asks for a confirmation (and, the first time, to allow installs
  from DaniTube). The child never sees the install screen. The videos live in the library folder, so
  an update doesn't touch them.
- **Cambiar PIN**, storage used and free space, and *Salir de la app*.

The PIN is created the first time you open the parent area and is stored only as a salted hash.
If you forget it, clear the app's data in Android settings (this also deletes the videos, so back
up first).

## Tech stack

| | |
|---|---|
| Language / UI | Kotlin, Jetpack Compose, Material 3 |
| Architecture | MVVM, Hilt, Kotlin Flow |
| Downloads | [youtubedl-android](https://github.com/JunkFood02/youtubedl-android) (yt-dlp + ffmpeg) |
| Background work | WorkManager (foreground service for downloads, daily yt-dlp update) |
| Playback | Media3 ExoPlayer |
| Storage | Room (metadata), videos and thumbnails in a parent-picked folder (app-private until one is picked) |
| Images | Coil |
| Library folder | Storage Access Framework (`DocumentFile`) |

`minSdk` 26, `targetSdk` / `compileSdk` 35.

## Getting started

1. Install [Android Studio](https://developer.android.com/studio). It bundles the JDK, and the
   setup wizard installs the SDK and emulator.
2. Open this folder. Studio creates the Gradle wrapper and syncs the project.
3. Run the `app` configuration on an emulator (use an arm64 image on Apple Silicon) or a phone
   with USB debugging.

The first download is slower than the rest: the app unpacks yt-dlp and updates it to the latest
version. Downloads need an internet connection; playback does not.

### Tests

```
./gradlew test
```

`YoutubeLinkTest` covers the link parser (watch, `youtu.be`, Shorts, playlists, shared text).

## How it works

```
app/src/main/java/com/danitube/app/
├── DaniTubeApp.kt            Hilt app, WorkManager config, schedules the daily yt-dlp update
├── data/
│   ├── BackupManager.kt      Library folder: import, move in, video list
│   ├── PinStore.kt           Salted-hash PIN in SharedPreferences
│   ├── VideoRepository.kt
│   ├── db/                   Room: VideoEntity, VideoDao, AppDatabase (+ migration 1→2)
│   ├── suggest/              SuggestionEngine (taste profile, daily search), worker, Wi-Fi monitor, switch
│   ├── update/               UpdateManager (check, download, verify, install), worker, install receiver
│   └── download/             YoutubeLink parser, YtDlpDownloader, DownloadWorker,
│                             DownloadScheduler, YtDlpUpdateWorker, VideoStorage
├── di/AppModule.kt
└── ui/
    ├── MainActivity.kt       Kid mode (feed, tabs, search) and the parent screens
    ├── add/                  Add video screen and ShareActivity (share target)
    ├── library/              Feed grid (Home and Favoritos)
    ├── player/               Shorts pager, ExoPlayer per page
    ├── parent/               PIN gate, parent menu, manage videos, folder
    └── theme/
```

**Download flow.** A link is normalised by `YoutubeLink`, previewed through yt-dlp, then queued
as a WorkManager job (one job per link). Jobs run one at a time. yt-dlp writes `<id>.mp4`, a jpg
thumbnail and an info json into the app's private `files/videos` folder; the info json is turned
into a Room row. Videos are capped at 720p to save space.

**Keeping yt-dlp working.** YouTube changes often and old yt-dlp copies stop working. The app
updates yt-dlp on first use, once a day on Wi-Fi, and automatically when a download or preview
fails (then retries once).

**Share target.** `ShareActivity` receives text shared from other apps. It is separate from
`MainActivity` and asks for the PIN before showing anything.

## Known limitations

- Suggestions are picked by tags with no human review. Videos are filtered for live streams, age
  restrictions and length, but YouTube has no "made for kids" filter, so check *Administrar videos*
  now and then and delete anything that doesn't fit.
- Videos downloaded before suggestions existed have no tags; the app fetches them 10 per day.
- Streaming needs yt-dlp to work, so it breaks whenever downloads break.

- Nothing stops the phone's Home or Recents buttons, so she can leave the app. Screen pinning was
  tried and removed. A silent kiosk (device-owner mode) would need a one-time `adb` setup.
- The PIN keeps a toddler out; it is not strong protection against an older child.
- Until a folder is picked, videos stay in app-private storage and are lost on uninstall.
- Switching to a different folder does not move videos already stored in the old one.
- Moving files into the folder happens inside the app, so keep it open while it runs.
- yt-dlp can still break between updates if YouTube changes something very recently.
- The UI text is Spanish only.
