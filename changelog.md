# Medusa: Changelog

Published builds only (what is live, or was live, on the `updates` branch). Newest first.
Read this before making any change (`rules.md`). Add an entry in the same session as every publish.

| Version | Build | Date | APK | Changes |
|---|---|---|---|---|
| 1.3 | 6 | 2026-11-20 | `medusa-1.3-b6.apk` | Icon and TV banner sized per density, all lossless PNG. Banner 640x360 (xhdpi, exact for the TV) plus 960x540 / 1280x720, smoothing on. Icon foreground 108 to 432 px. Build-5 hi-res files removed. |
| 1.3 | 5 | 2026-11-20 | `medusa-1.3-b5.apk` | Icon and TV banner shipped as single hi-res unscaled files (1152 px icon, 1280x720 banner in `drawable-nodpi`). Old low-res icon/banner files removed. Known issue: looks worse on the TV (launcher downscales them with a cheap filter); fix planned. |
| 1.3 | 4 | 2026-11-20 | `medusa-1.3-b4.apk` | New Medusa app icon (adaptive, from "Glossy Medusa Emblem Portrait") and new TV banner. APKs now named by build (`medusa-<versionName>-b<versionCode>.apk`); versionName only changes for major updates. |
| 1.2 | 3 | 2026-11-20 | `medusa-1.2.apk` | Update test: TV updated itself from 1.1 to 1.2. Self-update loop confirmed. |
| 1.1 | 2 | 2026-11-20 | `medusa-1.1.apk` | First clean build: home screen (brand, title, version card) and "Check for updates" self-updater. Sideloaded on the TV. |
| 1.0 | 1 | 2026-11-20 | `medusa-1.0.apk` | Broken: published from Android Studio's Run output (testOnly), TV refused it. Do not reuse. |
