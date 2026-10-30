# AGENTS.md

Guidance for AI coding agents (and humans) working on DaniTube. The README covers what the app does
for users; this file covers how to change it without breaking it.

## What this is

A private Android app for a toddler: a YouTube-style video app with no ads, no recommendations and
no links out. Videos are downloaded with yt-dlp, kept in a folder the parent picks, and played
offline. A daily job also finds similar videos and streams or downloads them, on Wi-Fi only.
Everything that is not "browse and watch" sits behind a parent PIN.

Personal use only: downloading from YouTube breaks its terms of service. Never add Play Store
publishing, analytics, ads, accounts or anything that sends data off the phone. The one exception is
the update check, which only asks GitHub for the latest release and sends nothing about the user.

## Stack

Kotlin, Jetpack Compose (Material 3), Hilt, Room, WorkManager, Media3 ExoPlayer, Coil,
[youtubedl-android](https://github.com/JunkFood02/youtubedl-android) (yt-dlp + ffmpeg).
`minSdk` 26, `targetSdk`/`compileSdk` 35, JVM target 17. Single module, `app`.

## Commands

```
./gradlew test                 # unit tests (JVM only)
./gradlew assembleDebug        # debug APK
./gradlew assembleRelease      # signed only if a keystore is configured (see Releases)
```

Gradle needs a JDK. If `java` is not on the PATH, point `JAVA_HOME` at Android Studio's bundled one:
`export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
Release lint is turned off in `app/build.gradle.kts` because it crashes on very new JDK versions;
don't turn it back on without checking.

There is no instrumented-test setup and no emulator in CI. A green `./gradlew test assembleDebug`
proves it compiles and the link parser works, not that the app behaves. Say so when you finish a
change that you could not run on a device.

## Layout

```
app/src/main/java/com/danitube/app/
├── DaniTubeApp.kt        Hilt app, WorkManager config, schedules the two periodic workers
├── data/
│   ├── VideoRepository   The one place that adds/deletes videos and records plays
│   ├── BackupManager     Library folder: pick it, import its videos.json, move videos into it
│   ├── BackupFolderStore Remembers the picked folder (SAF tree Uri)
│   ├── PinStore          Salted-hash PIN
│   ├── db/               Room: VideoEntity, SuggestionEntity, DAOs, AppDatabase + migrations
│   ├── download/         YoutubeLink parser, YtDlpDownloader, DownloadWorker/Scheduler,
│   │                     VideoStorage, YtDlpUpdateWorker
│   ├── suggest/          SuggestionEngine + worker, UnmeteredNetwork, SuggestionSettings
│   └── update/           UpdateManager (GitHub release check, download, verify, install), worker
├── di/AppModule.kt
└── ui/
    ├── MainActivity      Kid mode and the parent screens (simple state, no nav library)
    ├── add/              Add video screen (links + YouTube search), ShareActivity
    ├── library/          Feed, VideoCatalog (library + streamed videos), VideoItem
    ├── player/           Shorts-style pager, one ExoPlayer per page
    ├── parent/           PIN gate, parent menu, manage videos, library folder, suggestions switch
    └── theme/
```

## Rules that are easy to break

**Kid mode is locked down on purpose.** The child can browse, search the library, and watch. No
screen she can reach may link out, show a browser, open YouTube, or expose delete/add/settings.
Parent features go behind `ParentGate`. Back from the home screen is swallowed so she can't leave.
The app is locked to portrait; the player has a rotate button (like YouTube's) that toggles
landscape and resets to portrait when she leaves the player.

**Wi-Fi only for streaming, auto-download and updates.** Streaming a suggested video, the daily
suggestion/auto-download job and the app update download must never use mobile data or a metered network. Use
`UnmeteredNetwork`, and `NetworkType.UNMETERED` for scheduled work (`unmeteredOnly = true` in
`DownloadScheduler.enqueue`). If you add anything that talks to the internet on its own, apply the
same rule. Manual downloads the parent starts are the exception and use any connection (the update button is
not: the APK is large, so it stays Wi-Fi only).

**Self-update must stay parent-only and verified.** `UpdateManager` downloads the newest GitHub
release APK and checks that it is this package, newer, and signed with the same key before it
offers it. Installing starts only from the parent area, never from anything the child can reach.
The release tag maps to the version code with `major*10000 + minor*100 + patch`; keep
`UpdateManager.versionCodeOf` and the workflow in step, or updates stop being offered.

**Never touch video files directly; go through `VideoStorage`.** Videos live in the parent-picked
folder (SAF `DocumentFile`, so they survive updates and uninstalls) or, until a folder is picked or
when it is unavailable, in app-private storage. yt-dlp can only write real paths, so it downloads
into the private folder and `BackupManager.store` moves the files into the library afterwards.
- Entities store file *names*, not paths. Resolve with `VideoStorage.uri(name)`. It is blocking
  (it may list the folder), so call it off the main thread, as `VideoCatalog` does.
- Delete with `VideoStorage.delete`, never `File.delete` alone.
- After files move, `VideoStorage.invalidate()` bumps `version` so lists re-resolve their Uris.
- The library folder keeps a `videos.json`. It is how a reinstall gets the library back, so any
  new `VideoEntity` field worth keeping must be written and read in `BackupManager`.
- Videos exist in one place only. Do not reintroduce a second copy to "back up".

**Room migrations are written by hand.** `exportSchema = false`, so nothing checks them at build
time. Changing an entity means: bump `version`, add a `Migration` that produces exactly the schema
Room expects (types, nullability, `DEFAULT`s), and register it in `AppModule`. Never use
`fallbackToDestructiveMigration`: it would wipe the library list. There is no migration test;
review the SQL against the entity by eye.

**yt-dlp breaks regularly.** YouTube changes often and old yt-dlp copies stop working. The app
updates it on first use, daily on Wi-Fi, and after a failure (`withUpdateRetry`). When downloads,
search, previews or streaming all fail together, suspect yt-dlp or the youtubedl-android version in
`gradle/libs.versions.toml` first. Failures of the update are logged under `YtDlpDownloader`.

**Suggestions have no human review.** `SuggestionEngine` picks by tags and filters out live,
age-restricted and long videos, but YouTube has no "made for kids" filter. Keep the parent in
control: the on/off switch, deletion in *Administrar videos*, and "deleted means never suggested
again" (`STATUS_REJECTED`) must keep working. Don't widen what gets auto-downloaded without a limit.

## Conventions

- **Language:** UI strings are Spanish and live in `res/values/strings.xml` (no hardcoded text in
  Compose). Code, comments, commit messages and docs are English.
- **Style:** match the surrounding code. Comments say *why*, are short, and are sparse. Prefer small
  files that follow the existing split (data / ui, one ViewModel per screen).
- **DI:** Hilt. Singletons for stores and managers, `@HiltViewModel` for screens, `@HiltWorker` for
  workers. Workers must not fail a user-visible job because a side task failed (see how
  `DownloadWorker` wraps `backup.store` and `backup.sync` in `runCatching`).
- **Coroutines:** blocking file, SAF and yt-dlp work runs on `Dispatchers.IO`. Rethrow
  `CancellationException` whenever you catch `Exception`.
- **Tests:** JVM unit tests only, under `app/src/test`. Pure logic (like `YoutubeLink`) gets a test.
  Add one when you add parsing or scoring logic that can be separated from Android.

## Git and releases

- Use [Conventional Commits](https://www.conventionalcommits.org): `feat:`, `fix:`, `ci:`, `docs:`...
  The release workflow reads them. Work on a branch and open a PR; PR titles should be conventional
  too, because a squash merge uses the title as the commit message.
- Every push to `main` runs `.github/workflows/release.yml`: it picks the next version from the
  commits since the last tag (`feat!:` major, `feat:` minor, else patch; docs/ci/chore-only pushes
  don't release), runs the tests, builds a signed APK, creates the `vX.Y.Z` tag and publishes the
  release. The version code is `major*10000 + minor*100 + patch`, so it always rises with the tag.
- Signing: the key is never in the repo. Locally it comes from `~/.danitube/keystore.properties`;
  in CI from the secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. An
  update only installs over an existing app if it is signed with the same key, so don't change it.
- Don't commit keystores, `local.properties`, or build output.

## Before you say it's done

1. `./gradlew test assembleDebug` passes.
2. If you changed an entity: the migration exists, is registered, and `videos.json` handling in
   `BackupManager` still round-trips the new field.
3. If you touched the player, feed or add screen: say plainly what you could not check without a
   device (keyboard behavior, Wi-Fi switching, SAF providers, rotation).
4. If you changed user-facing behavior: update the README.
