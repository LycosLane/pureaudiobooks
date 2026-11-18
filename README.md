# PureAudiobooks

[![Platform: Android](https://img.shields.io/badge/Platform-Android%2010%2B-0078D6.svg)](https://github.com/LycosLane/pureaudiobooks)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Zero Telemetry](https://img.shields.io/badge/Telemetry-Zero-brightgreen.svg)]()
[![No Ads](https://img.shields.io/badge/Ads-None-success.svg)]()

> **A straightforward, privacy-first, open-source audiobook player & library manager for Android.**  
> Point it at your audiobook folder, resolve messy tags cleanly, and listen offline without accounts, ads, or tracking.

---

## Features

* **SAF Folder Scanning:** Pick any folder on your device or SD card via Android's Storage Access Framework. Supports flat folders (`Book/`), author subfolders (`Author/Book/`), multi-disc folders (`CD1/`, `CD2/`), and `.mp3`, `.m4b`, `.m4a`, `.aac`, `.ogg`, `.opus`, `.flac`, `.wav`.
* **Natural Track Sorting:** Ensures `Track 2` always plays before `Track 10`.
* **Smart Tag Conflict Resolution:** Detects missing, mismatched, or multiple `ALBUM` tags in a folder and lets you choose between folder name, detected tags, custom metadata, or splitting one folder into separate books by Album tag.
* **Optional Online Metadata Lookup (Off by Default):** Opt-in search via iTunes Audiobook & Open Library APIs using only built-in `HttpURLConnection` + `org.json` with SSRF and HTTPS downgrade protections. Saves results as simple `info.txt` + `cover.jpg` sidecar files in your audiobook folder.
* **Audiobook Playback:** Built on AndroidX Media3 (`ExoPlayer` + `MediaSessionService`) with per-book playback speed (`0.75x` – `3.0x`), silence skipping, smart rewind after pauses, audio normalization, sleep timer (minutes or end of chapter), bookmarks, and Bluetooth Next/Prev configuration.
* **Trash & Auto-Cleanup:** Soft-delete audiobooks to an in-app trash with configurable retention (`0h` / `24h` / `48h` / `72h`) and optional auto-cleanup for finished books.
* **Backup & Localization:** Full JSON backup/restore for progress, bookmarks, and settings. Built-in English and German UI.

---

## Technical Overview

PureAudiobooks is written in Kotlin using Jetpack Compose (Material 3), Room, and AndroidX Media3 — with **zero third-party runtime dependencies**.

```bash
# Run unit tests
gradle testDebugUnitTest

# Run Android Lint
gradle lintDebug

# Build debug APK
gradle assembleDebug
```

## License

MIT License © 2026 LycosLane
