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

- Paste or share a YouTube URL into the app.
- Save MP3 files into `Download/yt2mp3`.
- Show progress and ETA while `yt-dlp` runs.
- Cancel the active conversion.
- Update the bundled `yt-dlp` engine from the app when extractors need refresh.
- ABI split debug APKs for smaller GitHub artifacts.

## Build

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug packageDebugApks
```

The packaged APKs are written to `artifacts/`.

## Notes

The app uses `io.github.junkfood02.youtubedl-android` version `0.18.1`,
including its FFmpeg module. YouTube extraction can change over time; use the
in-app engine update button if a URL that should be downloadable starts failing.
