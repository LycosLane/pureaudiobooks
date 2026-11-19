<p align="center">
  <img src="assets/icons/app_icon.png" width="128" alt="PureAudiobooks Fedora Book Icon" />
</p>

# PureAudiobooks

[![Version: 1.1.0](https://img.shields.io/badge/version-1.1.0-amber.svg)](https://github.com/LycosLane/PureAudiobooks/releases)
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
* **Optional Online Metadata Lookup (Off by Default):** Opt-in search via iTunes Audiobook & Open Library APIs using only built-in `HttpURLConnection` + `org.json` with SSRF and HTTPS downgrade protections. Saves results as simple `info.txt` + `cover.jpg` sidecar files in your audiobook folder.

### Playback & Audio DSP
* **Zero-Friction Resume:** Remembers your last played audiobook across app restarts so you can press Play immediately from the Mini-Player or Library card.
* **Unified Player Bar:** Fine playback speed (`0.50x` - `3.00x` in `0.02x` steps) with live speed-adjusted remaining time, silence trimming (`Off`, `Soft`, `Medium`, `Strong`), live Mono / Audio-Fix (`L+R`, `Smart`, `L`, `R`), and a flexible sleep timer (`5` - `120` min or end of chapter).
* **Global & Per-Audiobook Settings:** Configure app-wide defaults in **Global settings**, override speed, silence trimming, volume normalization (`Global | Off | On`), and Mono audio per audiobook in **Audiobook settings**, or fix broken stereo channels per chapter.
* **Bookmarks & Bluetooth:** Save timestamped notes and configure Bluetooth Next/Prev buttons (`-10s` / `+30s` seek or chapter skip).

### Appearance, Trash & Backup
* **Light & Dark Themes:** Choose System, Light, or Dark mode, plus 6 curated accent colors separately for Light and Dark mode (Amber default).
* **Trash & Auto-Cleanup:** Soft-delete audiobooks to an in-app trash with configurable retention (`0h` / `24h` / `48h` / `72h`) and optional auto-cleanup for finished books.
* **Backup & Localization:** Full JSON backup/restore for progress, per-book audio overrides, bookmarks, themes, and settings. Built-in English and German UI.

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
