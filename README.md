<p align="center">
  <img src="assets/icons/app_icon.png" width="128" alt="PureAudiobooks Fedora Book Icon" />
</p>

# PureAudiobooks

[![Version: 1.1.2](https://img.shields.io/badge/version-1.1.2-amber.svg)](https://github.com/LycosLane/PureAudiobooks/releases)
[![Platform: Android](https://img.shields.io/badge/Platform-Android%2010%2B-0078D6.svg)](https://github.com/LycosLane/PureAudiobooks)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Zero Telemetry](https://img.shields.io/badge/Telemetry-Zero-brightgreen.svg)]()
[![No Ads](https://img.shields.io/badge/Ads-None-success.svg)]()

> **A straightforward, privacy-first, open-source audiobook player & library manager for Android.**  
> Point it at your audiobook folder, track remaining listening time at a glance, and listen offline without accounts, ads, or tracking.

---

## Key Features

### Library & Listening States
* **SAF Folder Scanning:** Pick any folder on your device or SD card via Android's Storage Access Framework. Supports flat folders (`Book/`), author subfolders (`Author/Book/`), multi-disc folders (`CD1/`, `CD2/`), and `.mp3`, `.m4b`, `.m4a`, `.aac`, `.ogg`, `.opus`, `.flac`, `.wav`.
* **Clear Listening Separation:** Separates **Continue listening** (with remaining time adjusted for playback speed, progress bar, and 1-tap Play/Pause) from **Not started** (calm layout showing total duration) and **Finished** books.
* **Hide Without Deleting:** Keep books safely on disk while hiding them from your everyday library, and unhide them anytime from the **Hidden** filter.
* **Grid & List Views + Optional Search & Refresh:** Switch both in-progress and unstarted sections together between grid and list layouts, and toggle the refresh button or search bar in Global settings.

### Metadata & Tag Resolution
* **Natural Track Sorting:** Ensures `Track 2` always plays before `Track 10`.
* **Smart Tag Conflict Resolution:** Detects missing, mismatched, or multiple `ALBUM` tags in a folder and lets you choose between folder name, detected tags, custom metadata, or splitting one folder into separate books by Album tag.
* **Cover Lightbox & Side-by-Side Online Diff:** Tap covers for a pinch-zoom lightbox with gallery cover picking, and compare multi-candidate iTunes & Open Library search results field-by-field before saving `info.txt` + `cover.jpg` sidecar files.
* **Chapter Batch Actions:** Tap chapters to mark up to a chapter as finished, reset from a chapter onward, or remove listened chapters cleanly.

### Playback & Audio DSP
* **Zero-Friction Resume & Larger Mini-Player:** Remembers your last played audiobook across app restarts with a prominent mini-player bar that opens the full player on tap.
* **Unified Player Bar:** Fine playback speed (`0.50x` - `3.00x` in `0.02x` steps) with live speed-adjusted remaining time, silence trimming (`Off`, `Soft`, `Medium`, `Strong`), live Stereo / Mono-Fix (`L+R`, `Smart`, `L`, `R`), and a flexible sleep timer (`5` - `120` min or end of chapter).
* **Symmetric Global & Per-Audiobook Settings:** Configure defaults for speed, silence trimming, volume normalization, Mono audio, and Smart-Rewind after pause globally, override any setting per audiobook with clear `Global` inheritance, expand long-press quick settings into the full sheet, or apply a book's setup to all audiobooks at once.
* **Bookmarks & Bluetooth:** Save timestamped notes and configure Bluetooth Next/Prev buttons (`-10s` / `+30s` seek or chapter skip).

### Appearance, Trash & Backup
* **Light & Dark Themes & UI Zoom:** Choose System, Light, or Dark mode with separate Light and Dark accents, adjust `Text & UI Zoom` (`80%` - `160%`) globally, and pinch-zoom book info text directly or in the fullscreen reader.
* **Trash & Auto-Cleanup:** Soft-delete audiobooks to an in-app trash with an explicit `Auto-delete trash` toggle (off by default; `Immediately` / `24h` / `48h` / `72h`) and optional `Auto-move finished books to trash` rules.
* **Backup & Localization:** Full JSON backup/restore for progress, per-book audio overrides, bookmarks, themes, UI/text zoom, and settings. Built-in English and German UI.

---

## Installation

Download the latest signed APK from the [**Releases**](https://github.com/LycosLane/PureAudiobooks/releases) page, or build from source:

```bash
git clone https://github.com/LycosLane/PureAudiobooks.git
cd PureAudiobooks
gradle assembleDebug
```

---

## License

MIT License © 2026 LycosLane
