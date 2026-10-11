# yt2mp3

Android app that accepts a YouTube URL, extracts the audio with the bundled
yt-dlp Android engine, transcodes it to MP3 with bundled FFmpeg, and saves the
result under:

```text
Download/yt2mp3
```

Only download content that you own, created, licensed, or otherwise have the
right to save.

## Features

- Basic MP3 editor: choose an audio file, preview it, and add named from/to ranges.
- Select, edit, remove, and reorder chunks; save one chunk or merge checked chunks into a named new MP3.
- Editor exports preserve the source file and re-encode audio for precise cuts. Use seconds, `mm:ss`, or `hh:mm:ss` with optional decimal fractions.
- Redesigned dark/light interface with separate link, output name, and status cards.
- Clear the URL field with one tap, while keeping your chosen output name.
- Choose an MP3 filename or leave it blank to use the video title; duplicate names do not overwrite existing files.
- Paste or share a YouTube URL into the app.
- Save MP3 files into `Download/yt2mp3`.
- Show progress and ETA while `yt-dlp` runs.
- Refresh the downloader engine before conversion once per day.
- Convert in private temporary storage, then publish the finished MP3 through MediaStore on Android 10+.
- Copy the complete conversion error, including app, Android, and engine versions.
- Cancel the active conversion.
- Repair missing or incomplete cached Python/FFmpeg runtime files at startup.
- Verify that Python can execute yt-dlp before showing the converter as ready.
- Update the bundled `yt-dlp` engine from the app when extractors need refresh.
- ABI split debug APKs for smaller GitHub artifacts.

## Build

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug packageDebugApks
```

The packaged APKs are written to `artifacts/`.

Runtime recovery regression test (on a connected x86_64 emulator):

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pyt2mp3Abi=x86_64
```

The test reproduces the missing `libpython3.12.so.1.0` linker error and verifies
automatic repair followed by successful yt-dlp execution. Published APKs still
default to Samsung-compatible `arm64-v8a`.

GitHub Actions verifies builds and uploads CI artifacts. Public releases are
signed locally with the established certificate; CI-generated debug keys are
not used for public updates.

## Updates

APK releases must support in-place update over the previous version. Keep
`applicationId` as `com.andre.yt2mp3`, keep the same signing certificate for the
current release line, and increment `yt2mp3VersionCode`/`yt2mp3VersionName`
before publishing. See `UPDATES.md` for the current signer fingerprint and the
known `v1.0.0` signing boundary.

## Notes

The app uses `io.github.junkfood02.youtubedl-android` version `0.18.1`,
including its FFmpeg module and bundled QuickJS runtime. The app enables the official yt-dlp EJS components for YouTube JavaScript challenges. YouTube extraction can change over time; use the
in-app engine update button if a URL that should be downloadable starts failing.

Conversion logs are kept in app-private storage. Temporary downloads and partial files are removed after each job. Only a completed, nonempty MP3 is published to `Download/yt2mp3`.
